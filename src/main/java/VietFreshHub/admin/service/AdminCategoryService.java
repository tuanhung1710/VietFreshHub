package VietFreshHub.admin.service;

import VietFreshHub.product.entity.Category;
import VietFreshHub.product.enums.CategoryStatus;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.admin.dto.AdminCategoryRequest;
import VietFreshHub.sellerapplication.service.CloudinaryStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private static final int MAX_SLUG_LENGTH = 200;
    private static final Pattern VALID_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Comparator<Category> CATEGORY_ORDER = Comparator
            .comparing(Category::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Category::getCategoryId, Comparator.nullsLast(Long::compareTo));

    private final CategoryRepository categoryRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Transactional(readOnly = true)
    public List<CategoryRow> list(String keyword, String requestedStatus) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String selectedStatus = normalizeStatus(requestedStatus);
        return flatten(categoryRepository.findAllByOrderByNameAsc()).stream()
                .filter(row -> matchesKeyword(row.category(), normalizedKeyword))
                .filter(row -> "ALL".equals(selectedStatus)
                        || row.category().getStatus() == CategoryStatus.valueOf(selectedStatus))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> parentOptions(Long editingCategoryId) {
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();
        Set<Long> excludedIds = descendantsIncluding(categories, editingCategoryId);
        return flatten(categories).stream()
                .filter(row -> !excludedIds.contains(row.category().getCategoryId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
    }

    @Transactional
    public Category create(AdminCategoryRequest request) {
        validateParent(request.getParentCategoryId(), null);

        Category category = new Category();
        applyForm(category, request, null);
        category.setStatus(CategoryStatus.ACTIVE);
        CloudinaryStorageService.UploadedCategoryImage uploadedImage = null;
        try {
            if (hasImageFile(request)) {
                uploadedImage = cloudinaryStorageService.uploadPublicCategoryImage(request.getImageFile());
                category.setImageUrl(uploadedImage.secureUrl());
            }
            Category savedCategory = categoryRepository.save(category);
            registerImageCleanup(uploadedImage, null);
            return savedCategory;
        } catch (RuntimeException exception) {
            if (uploadedImage != null) {
                cloudinaryStorageService.deleteUploadedCategoryImage(uploadedImage.publicId());
            }
            throw exception;
        }
    }

    @Transactional
    public Category update(Long categoryId, AdminCategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
        validateParent(request.getParentCategoryId(), categoryId);

        applyForm(category, request, categoryId);
        String previousImageUrl = category.getImageUrl();
        CloudinaryStorageService.UploadedCategoryImage uploadedImage = null;
        try {
            if (hasImageFile(request)) {
                uploadedImage = cloudinaryStorageService.uploadPublicCategoryImage(request.getImageFile());
                category.setImageUrl(uploadedImage.secureUrl());
            } else if (request.isRemoveImage()) {
                category.setImageUrl(null);
            }
            Category savedCategory = categoryRepository.save(category);
            if (uploadedImage != null || (request.isRemoveImage() && previousImageUrl != null)) {
                registerImageCleanup(uploadedImage, previousImageUrl);
            }
            return savedCategory;
        } catch (RuntimeException exception) {
            if (uploadedImage != null) {
                cloudinaryStorageService.deleteUploadedCategoryImage(uploadedImage.publicId());
            }
            throw exception;
        }
    }

    private boolean hasImageFile(AdminCategoryRequest request) {
        return request.getImageFile() != null && !request.getImageFile().isEmpty();
    }

    private void registerImageCleanup(
            CloudinaryStorageService.UploadedCategoryImage uploadedImage,
            String previousImageUrl
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousImageUrl != null) {
                        cloudinaryStorageService.deleteManagedCategoryImageByUrl(previousImageUrl);
                    }
                } else if (uploadedImage != null) {
                    cloudinaryStorageService.deleteUploadedCategoryImage(uploadedImage.publicId());
                }
            }
        });
    }

    @Transactional
    public void changeStatus(Long categoryId, CategoryStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Trạng thái danh mục không hợp lệ.");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
        category.setStatus(status);
        categoryRepository.save(category);
    }

    private void applyForm(Category category, AdminCategoryRequest request, Long excludedCategoryId) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > 150) {
            throw new IllegalArgumentException("Tên danh mục không hợp lệ.");
        }

        category.setName(name);
        category.setParentCategoryId(request.getParentCategoryId());
        String description = request.getDescription() == null ? null : request.getDescription().trim();
        category.setDescription(description == null || description.isEmpty() ? null : description);
        category.setSlug(resolveSlug(request.getSlug(), name, excludedCategoryId));
    }

    private String resolveSlug(String requestedSlug, String name, Long excludedCategoryId) {
        String trimmedSlug = requestedSlug == null ? "" : requestedSlug.trim();
        boolean generated = trimmedSlug.isEmpty();
        String slug = generated ? slugBase(name) : trimmedSlug.toLowerCase(Locale.ROOT);
        if (slug.length() > MAX_SLUG_LENGTH || !VALID_SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug chỉ được gồm chữ cái không dấu, số và dấu gạch ngang, tối đa 200 ký tự.");
        }

        if (!generated) {
            if (slugExists(slug, excludedCategoryId)) {
                throw new IllegalArgumentException("Slug này đã được sử dụng cho danh mục khác.");
            }
            return slug;
        }

        String uniqueSlug = slug;
        int suffix = 2;
        while (slugExists(uniqueSlug, excludedCategoryId)) {
            String suffixText = "-" + suffix++;
            String base = slug.substring(0, Math.min(slug.length(), MAX_SLUG_LENGTH - suffixText.length()));
            uniqueSlug = base + suffixText;
        }
        return uniqueSlug;
    }

    private boolean slugExists(String slug, Long excludedCategoryId) {
        return excludedCategoryId == null
                ? categoryRepository.existsBySlugIgnoreCase(slug)
                : categoryRepository.existsBySlugIgnoreCaseAndCategoryIdNot(slug, excludedCategoryId);
    }

    private String slugBase(String name) {
        String ascii = Normalizer.normalize(name.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (ascii.isBlank()) {
            ascii = "danh-muc";
        }
        return ascii.substring(0, Math.min(ascii.length(), MAX_SLUG_LENGTH));
    }

    private void validateParent(Long parentCategoryId, Long currentCategoryId) {
        if (parentCategoryId == null) {
            return;
        }

        Map<Long, Category> categoriesById = categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(category -> category.getCategoryId() != null)
                .collect(Collectors.toMap(Category::getCategoryId, category -> category));
        if (!categoriesById.containsKey(parentCategoryId)) {
            throw new IllegalArgumentException("Danh mục cha không tồn tại.");
        }

        Set<Long> visitedIds = new HashSet<>();
        Long cursor = parentCategoryId;
        while (cursor != null) {
            if (Objects.equals(cursor, currentCategoryId)) {
                throw new IllegalArgumentException("Không thể chọn chính danh mục hoặc danh mục con làm danh mục cha.");
            }
            if (!visitedIds.add(cursor)) {
                throw new IllegalStateException("Cây danh mục hiện có vòng lặp; hãy sửa cây trước khi thay đổi danh mục này.");
            }
            Category ancestor = categoriesById.get(cursor);
            if (ancestor == null) {
                throw new IllegalStateException("Cây danh mục có tham chiếu cha không còn tồn tại.");
            }
            cursor = ancestor.getParentCategoryId();
        }
    }

    private Set<Long> descendantsIncluding(List<Category> categories, Long categoryId) {
        if (categoryId == null) {
            return Set.of();
        }
        Set<Long> excluded = new HashSet<>();
        excluded.add(categoryId);
        boolean changed;
        do {
            changed = false;
            for (Category category : categories) {
                if (category.getCategoryId() != null
                        && category.getParentCategoryId() != null
                        && excluded.contains(category.getParentCategoryId())) {
                    changed |= excluded.add(category.getCategoryId());
                }
            }
        } while (changed);
        return excluded;
    }

    private List<CategoryRow> flatten(List<Category> categories) {
        Map<Long, Category> categoriesById = categories.stream()
                .filter(category -> category.getCategoryId() != null)
                .collect(Collectors.toMap(Category::getCategoryId, category -> category));
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            Long parentId = category.getParentCategoryId();
            if (parentId == null || !categoriesById.containsKey(parentId)) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(category);
            }
        }
        roots.sort(CATEGORY_ORDER);
        childrenByParent.values().forEach(children -> children.sort(CATEGORY_ORDER));

        List<CategoryRow> rows = new ArrayList<>();
        Set<Long> visitedIds = new HashSet<>();
        for (Category root : roots) {
            appendRows(root, 0, categoriesById, childrenByParent, visitedIds, rows);
        }
        // Keep legacy orphaned or cyclic records visible so an admin can repair the tree.
        for (Category category : categories.stream().sorted(CATEGORY_ORDER).toList()) {
            if (category.getCategoryId() == null || !visitedIds.contains(category.getCategoryId())) {
                appendRows(category, 0, categoriesById, childrenByParent, visitedIds, rows);
            }
        }
        return rows;
    }

    private void appendRows(
            Category category,
            int depth,
            Map<Long, Category> categoriesById,
            Map<Long, List<Category>> childrenByParent,
            Set<Long> visitedIds,
            List<CategoryRow> rows
    ) {
        Long categoryId = category.getCategoryId();
        if (categoryId != null && !visitedIds.add(categoryId)) {
            return;
        }
        Category parent = category.getParentCategoryId() == null
                ? null
                : categoriesById.get(category.getParentCategoryId());
        int childCount = childrenByParent.getOrDefault(categoryId, List.of()).size();
        rows.add(new CategoryRow(category, parent == null ? null : parent.getName(), depth, childCount));
        for (Category child : childrenByParent.getOrDefault(categoryId, List.of())) {
            appendRows(child, depth + 1, categoriesById, childrenByParent, visitedIds, rows);
        }
    }

    private boolean matchesKeyword(Category category, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return contains(category.getName(), keyword)
                || contains(category.getSlug(), keyword)
                || contains(category.getDescription(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ALL";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return Set.of("ALL", "ACTIVE", "INACTIVE").contains(normalized) ? normalized : "ALL";
    }

    public record CategoryRow(Category category, String parentName, int depth, int childCount) {
    }
}
