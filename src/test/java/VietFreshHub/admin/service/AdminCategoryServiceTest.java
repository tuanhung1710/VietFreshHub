package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminCategoryRequest;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.enums.CategoryStatus;
import VietFreshHub.product.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminCategoryServiceTest {

    private CategoryRepository categoryRepository;
    private AdminCategoryService categoryService;
    private final Map<Long, Category> categories = new LinkedHashMap<>();
    private long nextId;

    @BeforeEach
    void setUp() {
        categories.clear();
        nextId = 1L;
        categoryRepository = (CategoryRepository) Proxy.newProxyInstance(
                CategoryRepository.class.getClassLoader(),
                new Class<?>[]{CategoryRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "findAllByOrderByNameAsc" -> categories.values().stream()
                            .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    case "findById" -> Optional.ofNullable(categories.get(arguments[0]));
                    case "existsBySlugIgnoreCase" -> containsSlug((String) arguments[0], null);
                    case "existsBySlugIgnoreCaseAndCategoryIdNot" -> containsSlug(
                            (String) arguments[0], (Long) arguments[1]);
                    case "save" -> save((Category) arguments[0]);
                    case "toString" -> "In-memory CategoryRepository test double";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        categoryService = new AdminCategoryService(categoryRepository);
    }

    @Test
    void createGeneratesVietnameseSlugAndStartsActive() {
        AdminCategoryRequest request = request("Trái cây Việt Nam", null);

        Category saved = categoryService.create(request);

        assertEquals("trai-cay-viet-nam", saved.getSlug());
        assertEquals(CategoryStatus.ACTIVE, saved.getStatus());
        assertEquals(1, categories.size());
    }

    @Test
    void createRejectsDuplicateManuallySpecifiedSlug() {
        AdminCategoryRequest request = request("Trái cây", "trai-cay");
        seed(category(1L, null, "Danh mục đã có", "trai-cay"));

        assertThrows(IllegalArgumentException.class, () -> categoryService.create(request));
        assertEquals(1, categories.size());
    }

    @Test
    void updateRejectsSelectingDescendantAsParent() {
        Category root = category(1L, null, "Trái cây");
        Category child = category(2L, 1L, "Trái cây Việt Nam");
        AdminCategoryRequest request = request("Trái cây", "trai-cay");
        request.setParentCategoryId(2L);
        seed(root);
        seed(child);

        assertThrows(IllegalArgumentException.class, () -> categoryService.update(1L, request));
        assertNull(categories.get(1L).getParentCategoryId());
    }

    @Test
    void parentOptionsExcludeTheCategoryAndItsDescendants() {
        Category root = category(1L, null, "Trái cây");
        Category child = category(2L, 1L, "Trái cây Việt Nam");
        Category leaf = category(3L, 2L, "Trái cây miền Nam");
        Category otherRoot = category(4L, null, "Rau củ");
        seed(root);
        seed(child);
        seed(leaf);
        seed(otherRoot);

        List<AdminCategoryService.CategoryRow> options = categoryService.parentOptions(1L);

        assertEquals(List.of(4L), options.stream()
                .map(row -> row.category().getCategoryId())
                .toList());
    }

    @Test
    void generatedSlugGetsSuffixWhenAlreadyUsed() {
        AdminCategoryRequest request = request("Trái cây", null);
        seed(category(1L, null, "Trái cây cũ", "trai-cay"));

        Category saved = categoryService.create(request);

        assertEquals("trai-cay-2", saved.getSlug());
    }

    @Test
    void changeStatusCanDeactivateCategoryWithoutRemovingIt() {
        seed(category(1L, null, "Trái cây"));

        categoryService.changeStatus(1L, CategoryStatus.INACTIVE);

        assertEquals(CategoryStatus.INACTIVE, categories.get(1L).getStatus());
        assertEquals(1, categories.size());
    }

    private AdminCategoryRequest request(String name, String slug) {
        AdminCategoryRequest request = new AdminCategoryRequest();
        request.setName(name);
        request.setSlug(slug);
        return request;
    }

    private Category category(Long id, Long parentId, String name) {
        return category(id, parentId, name, name.toLowerCase(Locale.ROOT).replace(' ', '-'));
    }

    private Category category(Long id, Long parentId, String name, String slug) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setParentCategoryId(parentId);
        category.setName(name);
        category.setSlug(slug);
        category.setStatus(CategoryStatus.ACTIVE);
        return category;
    }

    private void seed(Category category) {
        categories.put(category.getCategoryId(), category);
        nextId = Math.max(nextId, category.getCategoryId() + 1);
    }

    private Category save(Category category) {
        if (category.getCategoryId() == null) {
            category.setCategoryId(nextId++);
        }
        categories.put(category.getCategoryId(), category);
        return category;
    }

    private boolean containsSlug(String slug, Long excludedCategoryId) {
        return categories.values().stream()
                .anyMatch(category -> !category.getCategoryId().equals(excludedCategoryId)
                        && category.getSlug() != null
                        && category.getSlug().equalsIgnoreCase(slug));
    }
}
