package VietFreshHub.product.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.reason;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.BulkProductActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductCategory;
import VietFreshHub.product.entity.ProductImage;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.exception.DuplicateProductNameException;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.ProductCategoryRepository;
import VietFreshHub.product.repository.ProductImageRepository;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.product.service.ProductCatalogQueries;
import VietFreshHub.product.service.ProductCatalogReader;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.product.validator.CategoryValidator;
import VietFreshHub.product.validator.ProductFormValidator;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import VietFreshHub.subscription.services.SubscriptionPlanService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Điều phối vòng đời sản phẩm/SKU và ghi nhận thay đổi trong cùng giao dịch. ProductFormValidator
 * kiểm tra dữ liệu biểu mẫu; ProductCatalogQueries và ProductCatalogReader phụ trách luồng đọc.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductCatalogQueries catalogQueries;
    private final ProductCatalogReader catalogReader;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final ShopService shopService;
    private final ShopRepository shopRepository;
    private final ShopSubscriptionService shopSubscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AuthService authService;
    private final CategoryValidator categoryValidator;
    private final ProductFormValidator productFormValidator;
    private final ProductAvailabilityService productAvailabilityService;

    /** Chuẩn hóa bộ lọc và truy vấn sản phẩm trong cửa hàng đang quản lý. */
    @Override
    public Page<ProductResponse> searchManagement(
            Authentication authentication, ProductCatalogFilter filters, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        prepareManagementFilters(filters);
        List<Long> activeCategories = productAvailabilityService.activeCategoryIds();
        return catalogQueries.searchManagement(shopId, filters, activeCategories, page, size);
    }

    private void prepareManagementFilters(ProductCatalogFilter filters) {
        filters.setQ(normalizeFilter(filters.getQ()));
        filters.setTab(normalizeFilter(filters.getTab()));
        filters.setSort(normalizeFilter(filters.getSort()));
        if (filters.getSort().isEmpty()) {
            filters.setSort("updated_desc");
        }
        require(
                List.of("", "active", "out-of-stock", "violation", "hidden")
                        .contains(filters.getTab()),
                "Tab sản phẩm không hợp lệ.");
        require(
                List.of("updated_desc", "updated_asc").contains(filters.getSort()),
                "Cách sắp xếp không hợp lệ.");

        LocalDate createdFrom = filters.getCreatedFrom();
        LocalDate createdTo = filters.getCreatedTo();
        require(
                createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo),
                "Ngày bắt đầu không được sau ngày kết thúc.");
        validateCatalogPriceRange(filters.getMinPrice(), filters.getMaxPrice());
    }

    /** Giá lọc cho phép 0 đồng, phải nguyên đồng và nằm trong giới hạn 16 chữ số. */
    private void validateCatalogPriceRange(BigDecimal minimumPrice, BigDecimal maximumPrice) {
        for (BigDecimal price : new BigDecimal[] {minimumPrice, maximumPrice}) {
            boolean validPrice =
                    price == null
                            || (price.signum() >= 0
                                    && price.stripTrailingZeros().scale() <= 0
                                    && (long) price.precision() - price.scale() <= 16);
            require(validPrice, "Giá lọc phải là số nguyên đồng thời không âm, tối đa 16 chữ số.");
        }
        require(
                minimumPrice == null
                        || maximumPrice == null
                        || minimumPrice.compareTo(maximumPrice) <= 0,
                "Giá từ không được lớn hơn giá đến.");
    }

    /** Đếm sản phẩm theo trạng thái quản lý dựa trên điều kiện kinh doanh và tồn kho hiện tại. */
    @Override
    public ProductTabSummary productTabSummary(Authentication authentication) {
        return productRepository.managementSummary(
                shopService.getManagedShopId(authentication),
                productAvailabilityService.activeCategoryIds(),
                today());
    }

    /** Tổng hợp số phân loại sắp hết hàng và số lô cận hạn, hết hạn cần xử lý trong cửa hàng. */
    @Override
    public InventoryAttentionSummary inventoryAttentionSummary(Authentication authentication) {
        return productRepository.inventoryAttentionSummary(
                shopService.getManagedShopId(authentication), today());
    }

    /** Loại bỏ khoảng trắng ở hai đầu; coi bộ lọc null là không có điều kiện lọc. */
    private String normalizeFilter(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /** Lấy chi tiết sản phẩm thuộc cửa hàng đang quản lý. */
    @Override
    public ProductResponse get(Authentication authentication, Long productId) {
        Product product = getOwnedProductForRead(authentication, productId);
        return catalogReader.forProduct(product);
    }

    /** Lấy phân loại trong sản phẩm đã kiểm tra quyền; từ chối nếu thuộc sản phẩm khác. */
    @Override
    public VariantResponse getVariant(
            Authentication authentication, Long productId, Long variantId) {
        ProductResponse product = get(authentication, productId);
        for (VariantResponse variant : product.getVariants()) {
            if (variant.getVariant().getVariantId().equals(variantId)) {
                return variant;
            }
        }
        throw new AccessDeniedException("Phân loại không thuộc sản phẩm này.");
    }

    /** Chọn cùng ảnh cấp sản phẩm với màn hình danh mục khi so sánh thay đổi nội dung. */
    private String productImageUrl(Long productId) {
        return ProductCatalogReader.primaryProductImageUrl(
                productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId));
    }

    /**
     * Kiểm tra còn phân loại đang bật, kể cả phân loại hết hàng.
     * Có thể bỏ qua một phân loại để kiểm tra điều kiện trước khi đổi trạng thái của chính phân loại đó.
     */
    private boolean hasOpenVariant(List<ProductVariant> variants, Long excludedVariantId) {
        for (ProductVariant variant : variants) {
            if (variant.getVariantId().equals(excludedVariantId)) {
                continue;
            }
            if (variant.getStatus() == VariantStatus.ACTIVE
                    || variant.getStatus() == VariantStatus.OUT_OF_STOCK) {
                return true;
            }
        }
        return false;
    }

    /** Lấy danh mục có toàn bộ chuỗi danh mục cha còn hoạt động, sắp theo tên. */
    @Override
    public List<Category> categories() {
        List<Category> activeCategories = new ArrayList<>();
        for (Category category : categoryRepository.findAllByOrderByNameAsc()) {
            if (categoryValidator.isActiveCategoryChain(category.getCategoryId())) {
                activeCategories.add(category);
            }
        }
        return activeCategories;
    }

    /** Trả về các đơn vị bán được hỗ trợ khi tạo hoặc cập nhật phân loại. */
    @Override
    public List<String> units() {
        return productFormValidator.units();
    }

    /** Đọc sản phẩm thuộc shop hiện tại mà không yêu cầu khóa ghi. */
    private Product getOwnedProductForRead(Authentication authentication, Long productId) {
        Long shopId = shopService.getManagedShopId(authentication);
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Sản phẩm không thuộc cửa hàng của bạn."));
        if (!product.getShopId().equals(shopId)) {
            throw new AccessDeniedException("Sản phẩm không thuộc cửa hàng của bạn.");
        }
        return product;
    }

    /** Khóa ghi rồi tải lại sản phẩm trong phạm vi shop trước khi cập nhật. */
    private Product getOwnedProductForUpdate(Authentication authentication, Long productId) {
        Long shopId = shopService.getManagedShopId(authentication);
        Product product =
                productRepository
                        .lockOwned(productId, shopId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Sản phẩm không thuộc cửa hàng của bạn."));
        return inventoryLockHelper.refresh(product);
    }

    /** Khóa và tải lại phân loại, đồng thời kiểm tra phân loại thuộc đúng sản phẩm. */
    private ProductVariant getVariantForUpdate(Long productId, Long variantId) {
        ProductVariant variant =
                productVariantRepository
                        .lockById(variantId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Phân loại không thuộc sản phẩm này."));
        inventoryLockHelper.refresh(variant);
        if (!variant.getProductId().equals(productId)) {
            throw new AccessDeniedException("Phân loại không thuộc sản phẩm này.");
        }
        return variant;
    }

    /** Chặn sửa sản phẩm đã ngừng kinh doanh; chỉ cho xem lịch sử và xử lý tồn kho. */
    private void requireWritableProduct(Product product) {
        require(
                product.getStatus() != ProductStatus.DELETED,
                "Sản phẩm đã xóa. Bạn chỉ có thể xem lịch sử và xử lý tồn kho.");
    }

    /** Tên phải duy nhất trong shop; khi sửa thì bỏ qua chính sản phẩm đang sửa. */
    private void validateUniqueName(Long shopId, String name, Long excludedProductId) {
        if (productRepository.countDuplicateNames(shopId, name.trim(), excludedProductId) > 0) {
            throw new DuplicateProductNameException();
        }
    }

    /** Tạo slug không dấu từ tên sản phẩm và nối ID để giữ slug riêng cho từng sản phẩm. */
    private String generateSlug(String productName, Long productId) {
        String text = productName.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String root = Normalizer.normalize(text, Normalizer.Form.NFD);
        root = root.replaceAll("\\p{M}", "");
        root = root.replaceAll("[^a-z0-9]+", "-");
        root = root.replaceAll("^-|-$", "");
        if (root.isBlank()) {
            root = "san-pham";
        }
        if (root.length() > 270) {
            root = root.substring(0, 270);
        }
        return root + "-" + productId;
    }

    /** Gán thông tin sản phẩm từ biểu mẫu; xóa hướng dẫn chuẩn bị khi không yêu cầu kiểm tra. */
    private void applyProductFields(Product product, ProductRequest form) {
        product.setName(form.getName().trim());
        product.setDescription(form.getDescription());
        product.setOrigin(form.getOrigin().strip());
        product.setExpiryWarningDays(form.getExpiryWarningDays());
        product.setRequiresPreparationCheck(form.isRequiresPreparationCheck());
        product.setPreparationNote(null);
        if (form.isRequiresPreparationCheck()) {
            product.setPreparationNote(form.getPreparationNote().trim());
        }
        product.setUpdatedAt(now());
    }

    /** Gán thông tin phân loại đã được kiểm tra và cập nhật thời điểm chỉnh sửa. */
    private void applyVariantFields(ProductVariant variant, VariantRequest form) {
        variant.setSku(form.getSku());
        variant.setVariantName(form.getVariantName().trim());
        variant.setThumbnailUrl(form.getThumbnailUrl().trim());
        variant.setPrice(form.getPrice());
        variant.setCompareAtPrice(form.getCompareAtPrice());
        variant.setWeight(form.getWeight());
        variant.setUnit(form.getUnit());
        variant.setLowStockThreshold(form.getLowStockThreshold());
        variant.setUpdatedAt(now());
    }

    /**
     * Cập nhật hoặc tạo ảnh chính cho sản phẩm hay phân loại; variantId null là ảnh cấp sản phẩm.
     */
    private void savePrimaryImage(Long productId, Long variantId, String url) {
        // Tìm lại ảnh chính của đúng cấp sản phẩm hoặc SKU để cập nhật, tránh tạo ảnh trùng.
        ProductImage primaryImage = new ProductImage();
        for (ProductImage image :
                productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId)) {
            if (Objects.equals(image.getVariantId(), variantId)
                    && Boolean.TRUE.equals(image.getPrimary())) {
                primaryImage = image;
                break;
            }
        }
        primaryImage.setProductId(productId);
        primaryImage.setVariantId(variantId);
        primaryImage.setImageUrl(url.trim());
        primaryImage.setPrimary(true);
        primaryImage.setSortOrder(0);
        if (primaryImage.getCreatedAt() == null) {
            primaryImage.setCreatedAt(now());
        }
        productImageRepository.save(primaryImage);
    }

    /**
     * Tạo sản phẩm cùng danh mục, ảnh và các phân loại.
     * Chỉ chuyển sang trạng thái mở bán sau khi đã lưu đủ dữ liệu liên quan.
     *
     * @return ID của sản phẩm vừa tạo.
     */
    @Override
    @Transactional
    public Long create(Authentication authentication, ProductRequest form) {
        // Kiểm tra toàn bộ sản phẩm và phân loại trước khi bắt đầu ghi dữ liệu liên quan.
        Long shopId = shopService.getManagedShopId(authentication);
        enforceProductQuota(shopId);
        Long actorId = authService.getCurrentUserId(authentication);
        productFormValidator.validateProductFields(form);
        validateUniqueName(shopId, form.getName(), null);
        productFormValidator.validateNewVariants(form);

        Product product = new Product();
        product.setShopId(shopId);
        applyProductFields(product, form);
        // Dùng slug tạm để lưu sản phẩm trước khi có ID tạo slug chính thức.
        product.setSlug("TMP-" + UUID.randomUUID());
        product.setStatus(ProductStatus.INACTIVE);
        product.setApprovalStatus(ApprovalStatus.PENDING);
        product.setCreatedAt(now());
        productRepository.saveAndFlush(product);
        product.setSlug(generateSlug(form.getName(), product.getProductId()));
        productRepository.saveAndFlush(product);
        productCategoryRepository.save(
                new ProductCategory(product.getProductId(), form.getCategoryId()));
        savePrimaryImage(product.getProductId(), null, form.getImageUrl());
        for (VariantRequest variant : form.getVariants()) {
            createVariant(product, variant, actorId);
        }
        // Lưu đủ danh mục và phân loại trước khi mở bán để thỏa điều kiện trigger trong cơ sở dữ
        // liệu.
        product.setSellerHidden(false);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
        inventoryJournalService.audit(
                actorId,
                "CREATE",
                "PRODUCT",
                product.getProductId(),
                "Tạo sản phẩm; mở bán và chờ hậu kiểm.");
        return product.getProductId();
    }

    void enforceProductQuota(Long shopId) {
        shopRepository
                .findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new AccessDeniedException("Không xác định được cửa hàng."));
        int maxProducts =
                shopSubscriptionService
                        .getCurrentSubscription(shopId)
                        .map(
                                subscription ->
                                        requireProductLimit(
                                                subscription.maxProducts(), "Gói đăng ký hiện tại"))
                        .orElseGet(this::freePlanProductLimit);
        long currentProducts =
                productRepository.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED);
        ensureProductCapacity(currentProducts, maxProducts);
    }

    static void ensureProductCapacity(long currentProducts, int maxProducts) {
        if (currentProducts >= maxProducts) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đã đạt giới hạn sản phẩm của gói đăng ký.");
        }
    }

    private int freePlanProductLimit() {
        try {
            var freePlan = subscriptionPlanService.getFreePlan();
            if (freePlan.status() != PlanStatus.ACTIVE
                    || freePlan.price() == null
                    || freePlan.price().signum() != 0) {
                throw new IllegalStateException("Gói Free phải hoạt động và có giá bằng 0.");
            }
            return requireProductLimit(freePlan.maxProducts(), "Gói Free");
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gói Free chưa được cấu hình hợp lệ; không thể tạo sản phẩm.",
                    ex);
        }
    }

    private int requireProductLimit(Integer maxProducts, String planName) {
        if (maxProducts == null || maxProducts <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    planName + " chưa có giới hạn sản phẩm hợp lệ.");
        }
        return maxProducts;
    }

    /** Tạo phân loại với SKU theo ID, lưu ảnh và giá ban đầu; tạo lô đầu tiên nếu có nhập hàng. */
    private void createVariant(Product product, VariantRequest form, Long actorId) {
        int quantity = productFormValidator.initialQuantity(form);
        ProductVariant variant = new ProductVariant();
        variant.setProductId(product.getProductId());
        applyVariantFields(variant, form);
        // Lưu SKU tạm để lấy ID rồi thay bằng mã chính thức trước khi hoàn tất giao dịch.
        variant.setSku("TMP-" + UUID.randomUUID());
        variant.setStatus(VariantStatus.OUT_OF_STOCK);
        if (quantity > 0) {
            variant.setStatus(VariantStatus.ACTIVE);
        }
        variant.setCreatedAt(now());
        productVariantRepository.saveAndFlush(variant);
        String sku = BusinessCodes.format("SKU", variant.getVariantId());
        variant.setSku(sku);
        productVariantRepository.saveAndFlush(variant);
        form.setSku(sku);
        savePrimaryImage(product.getProductId(), variant.getVariantId(), form.getThumbnailUrl());
        // Chỉ tạo lô và biến động kho khi người bán thực sự nhập số lượng ban đầu.
        if (quantity > 0) {
            createInitialBatch(variant, form, quantity, actorId);
        }
    }

    /**
     * Lưu lô nhập ban đầu với lượng giữ bằng 0, đồng thời ghi biến động kho và lịch sử thao tác.
     */
    private void createInitialBatch(
            ProductVariant variant, VariantRequest form, int quantity, Long actorId) {
        // Lô đầu tiên chứa lượng vừa nhập; chưa có phần nào được giữ cho đơn.
        InventoryBatch batch = new InventoryBatch();
        batch.setVariantId(variant.getVariantId());
        batch.setQuantityOnHand(quantity);
        batch.setReservedQuantity(0);
        batch.setCostPrice(form.getCostPrice());
        batch.setReceivedAt(InventoryRules.startOfDayUtc(form.getReceivedDate()));
        batch.setExpiryDate(form.getExpiryDate());
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setCreatedAt(now());
        batch.setUpdatedAt(now());
        inventoryBatchRepository.saveAndFlush(batch);
        // Cần ID lô trước khi ghi biến động nhập kho và lịch sử thao tác tham chiếu tới lô.
        inventoryJournalService.stock(
                variant.getVariantId(),
                batch.getBatchId(),
                StockTransactionType.RESTOCK,
                quantity,
                actorId,
                "Nhập kho ban đầu cho phân loại.",
                "BATCH",
                batch.getBatchId());
        inventoryJournalService.audit(
                actorId,
                "RESTOCK",
                "BATCH",
                batch.getBatchId(),
                "Nhập kho ban đầu: " + quantity + " " + variant.getUnit());
    }

    /**
     * Cập nhật sản phẩm và ghi lịch sử khi thông tin hoặc mốc nhắc hạn thay đổi.
     * Thay đổi nội dung cần hậu kiểm sẽ đặt lại trạng thái duyệt, giữ nguyên trạng thái kinh doanh.
     */
    @Override
    @Transactional
    public void update(Authentication authentication, Long productId, ProductRequest form) {
        // Khóa sản phẩm và kiểm tra quyền, dữ liệu, tên trùng trước khi xác định phần thay đổi.
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        productFormValidator.validateProductFields(form);
        validateUniqueName(product.getShopId(), form.getName(), productId);
        String changeReason = reason(form.getReason());
        boolean metadataChanged = productMetadataChanged(product, form);
        Integer previousReminderDays = product.getExpiryWarningDays();
        boolean reminderChanged =
                !Objects.equals(previousReminderDays, form.getExpiryWarningDays());
        applyProductFields(product, form);
        if (metadataChanged) {
            updateProductMetadata(product, productId, form);
        } else {
            productRepository.saveAndFlush(product);
        }
        if (metadataChanged || reminderChanged) {
            // Thay đổi nội dung và thay đổi mốc nhắc hạn có loại lịch sử khác nhau để dễ tra cứu.
            String auditReason = changeReason;
            if (reminderChanged) {
                auditReason +=
                        "; nhắc trước hạn sử dụng "
                                + previousReminderDays
                                + " → "
                                + form.getExpiryWarningDays()
                                + " ngày.";
            }
            inventoryJournalService.audit(
                    authService.getCurrentUserId(authentication),
                    metadataChanged ? "UPDATE" : "REMINDER",
                    "PRODUCT",
                    productId,
                    auditReason);
        }
    }

    /** Thay nội dung cần hậu kiểm trong cùng transaction, khôi phục trạng thái bán sau khi đổi danh mục. */
    private void updateProductMetadata(Product product, Long productId, ProductRequest form) {
        ProductStatus businessStatus = product.getStatus();
        resetApproval(product);
        // Tạm ẩn trong giao dịch để thay danh mục rồi khôi phục trạng thái kinh doanh.
        // Khóa của quản trị viên được giữ nguyên trong toàn bộ quá trình.
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        productCategoryRepository.deleteByIdProductId(productId);
        productCategoryRepository.flush();
        productCategoryRepository.save(new ProductCategory(productId, form.getCategoryId()));
        savePrimaryImage(productId, null, form.getImageUrl());
        productCategoryRepository.flush();
        product.setStatus(businessStatus);
        productRepository.saveAndFlush(product);
    }

    /** Phát hiện thay đổi nội dung cần hậu kiểm; mốc nhắc hạn sử dụng được xử lý riêng. */
    private boolean productMetadataChanged(Product product, ProductRequest form) {
        List<ProductCategory> categories =
                productCategoryRepository.findByIdProductId(product.getProductId());
        Long category = categories.isEmpty() ? null : categories.getFirst().getId().getCategoryId();
        String preparationNote =
                form.isRequiresPreparationCheck() ? form.getPreparationNote().trim() : null;
        String previousPreparationNote =
                Boolean.TRUE.equals(product.getRequiresPreparationCheck())
                        ? product.getPreparationNote()
                        : null;
        return !Objects.equals(product.getName(), form.getName().trim())
                || !Objects.equals(emptyToNull(product.getOrigin()), form.getOrigin())
                || !Objects.equals(
                        emptyToNull(product.getDescription()), emptyToNull(form.getDescription()))
                || !Objects.equals(category, form.getCategoryId())
                || !Objects.equals(
                        productImageUrl(product.getProductId()), form.getImageUrl().trim())
                || Boolean.TRUE.equals(product.getRequiresPreparationCheck())
                        != form.isRequiresPreparationCheck()
                || !Objects.equals(previousPreparationNote, preparationNote);
    }

    /** Coi chuỗi rỗng như null để so sánh các trường nội dung tùy chọn. */
    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Đổi trạng thái tối đa 50 sản phẩm trong cùng giao dịch.
     * Khóa theo thứ tự ID và kiểm tra toàn bộ lựa chọn; nếu một sản phẩm không hợp lệ thì hoàn tác tất cả.
     */
    @Override
    @Transactional
    public void changeStatuses(Authentication authentication, BulkProductActionRequest form) {
        require(
                form.getProductIds() != null
                        && !form.getProductIds().isEmpty()
                        && form.getProductIds().size() <= 50,
                "Chọn từ 1 đến 50 sản phẩm trên trang hiện tại.");
        require(
                form.getStatus() == ProductStatus.ACTIVE
                        || form.getStatus() == ProductStatus.INACTIVE
                        || form.getStatus() == ProductStatus.DELETED,
                "Chọn Tạm ẩn, Mở bán hoặc Xóa sản phẩm.");
        reason(form.getReason());
        require(
                form.getProductIds().stream().allMatch(id -> id != null && id > 0),
                "Danh sách sản phẩm không hợp lệ.");
        List<Long> ids = form.getProductIds().stream().distinct().sorted().toList();
        require(ids.size() == form.getProductIds().size(), "Danh sách sản phẩm không hợp lệ.");
        // Khóa theo ID và kiểm tra toàn bộ lựa chọn trước khi đổi trạng thái.
        // Nếu một sản phẩm bị từ chối, giao dịch hoàn tác cả trạng thái và lịch sử đã ghi.
        for (Long id : ids) {
            Product product = getOwnedProductForUpdate(authentication, id);
            requireWritableProduct(product);
            require(
                    form.getStatus() == ProductStatus.DELETED
                            || product.getStatus()
                                    == (form.getStatus() == ProductStatus.ACTIVE
                                            ? ProductStatus.INACTIVE
                                            : ProductStatus.ACTIVE),
                    "Trạng thái sản phẩm đã thay đổi. Tải lại danh sách và chọn các sản phẩm cùng điều kiện.");
            if (form.getStatus() == ProductStatus.DELETED) {
                for (ProductVariant variant :
                        productVariantRepository.findByProductIdOrderByVariantId(id)) {
                    require(
                            !hasReservedStock(variant.getVariantId()),
                            "Không thể xóa sản phẩm còn số lượng chờ xuất kho. Chưa có sản phẩm nào được thay đổi.");
                }
            }
        }
        for (Long id : ids) {
            changeStatus(authentication, id, form);
        }
    }

    /**
     * Đổi trạng thái kinh doanh và ghi lý do; khi ngừng kinh doanh sẽ ngừng toàn bộ phân loại.
     * Chặn mở bán sai điều kiện hoặc ngừng kinh doanh khi còn hàng giữ cho đơn.
     */
    @Override
    @Transactional
    public void changeStatus(Authentication authentication, Long productId, ActionRequest form) {
        // Giữ nguyên dữ liệu kho và lịch sử; thao tác này chỉ đổi trạng thái kinh doanh.
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        String changeReason = reason(form.getReason());
        require(form.getStatus() != null, "Chọn trạng thái sản phẩm hợp lệ.");
        List<ProductVariant> variants =
                productVariantRepository.findByProductIdOrderByVariantId(productId);
        if (form.getStatus() == ProductStatus.ACTIVE) {
            // Mở bán cần danh mục hợp lệ và ít nhất một SKU đang bật; chủ shop không tự gỡ khóa
            // Admin.
            require(
                    !product.isModerationBlocked(),
                    "Sản phẩm đang bị Admin khóa. Chủ shop không thể tự mở khóa.");
            require(
                    categoryValidator.hasActiveCategory(productId),
                    "Sản phẩm chưa có danh mục còn hoạt động.");
            require(
                    hasOpenVariant(variants, null),
                    "Sản phẩm cần có ít nhất một phân loại đang bật để mở bán.");
        }
        if (form.getStatus() == ProductStatus.DELETED) {
            for (ProductVariant variant : variants) {
                // Chưa được ngừng kinh doanh khi còn hàng giữ để thực hiện đơn đã nhận.
                require(
                        !hasReservedStock(variant.getVariantId()),
                        "Không thể xóa sản phẩm còn số lượng chờ xuất kho.");
            }
        }
        ProductStatus previousStatus = product.getStatus();
        product.setStatus(form.getStatus());
        product.setSellerHidden(form.getStatus() == ProductStatus.INACTIVE);
        product.setUpdatedAt(now());
        productRepository.saveAndFlush(product);
        if (form.getStatus() == ProductStatus.DELETED) {
            archiveVariants(variants);
        }
        if (previousStatus != form.getStatus()) {
            inventoryJournalService.audit(
                    authService.getCurrentUserId(authentication),
                    "STATUS",
                    "PRODUCT",
                    productId,
                    productStatusLabel(previousStatus)
                            + " → "
                            + productStatusLabel(form.getStatus())
                            + ": "
                            + changeReason);
        }
    }

    /** Kiểm tra có lô nào của phân loại vẫn còn hàng giữ cho đơn hàng. */
    private boolean hasReservedStock(Long variantId) {
        for (InventoryBatch batch :
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId)) {
            if (batch.getReservedQuantity() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Đánh dấu các phân loại là ngừng kinh doanh, giữ lại dữ liệu tồn kho và lịch sử. */
    private void archiveVariants(List<ProductVariant> variants) {
        for (ProductVariant variant : variants) {
            variant.setStatus(VariantStatus.DELETED);
            variant.setUpdatedAt(now());
            productVariantRepository.save(variant);
        }
    }

    /**
     * Thêm phân loại trong giới hạn 20 phân loại và yêu cầu hậu kiểm thông tin mới.
     */
    @Override
    @Transactional
    public void addVariant(Authentication authentication, Long productId, VariantRequest form) {
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        require(
                productVariantRepository.countByProductIdAndStatusNot(productId, VariantStatus.DELETED)
                        < 20,
                "Mỗi sản phẩm có tối đa 20 phân loại.");
        form.setSku(null);
        productFormValidator.validateVariantFields(form, null);
        productFormValidator.initialQuantity(form);
        String changeReason = reason(form.getReason());
        Long actorId = authService.getCurrentUserId(authentication);
        requestReapproval(product);
        // Thông tin SKU mới cần hậu kiểm; việc tạo SKU không tự đổi trạng thái bán của sản phẩm.
        createVariant(product, form, actorId);
        inventoryJournalService.audit(
                actorId,
                "ADD_SKU",
                "PRODUCT",
                productId,
                changeReason + "; thêm phân loại và yêu cầu hậu kiểm thông tin mới.");
    }

    /**
     * Cập nhật phân loại nhưng giữ nguyên SKU; ghi lịch sử khi giá hoặc nội dung thay đổi.
     * Thay đổi tên, đơn vị hoặc khối lượng sẽ yêu cầu hậu kiểm lại sản phẩm.
     */
    @Override
    @Transactional
    public void updateVariant(
            Authentication authentication, Long productId, Long variantId, VariantRequest form) {
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        ProductVariant variant = getVariantForUpdate(productId, variantId);
        require(variant.getStatus() != VariantStatus.DELETED, "Phân loại đã xóa.");
        // SKU không được sửa; luôn dùng mã đang lưu thay cho giá trị gửi từ trình duyệt.
        form.setSku(variant.getSku());
        productFormValidator.validateVariantFields(form, variant);
        String changeReason = reason(form.getReason());
        // Tách thay đổi cấu trúc, giá và trường khác để quyết định hậu kiểm và loại lịch sử cần
        // ghi.
        boolean structureChanged = variantStructureChanged(variant, form);
        boolean otherFieldsChanged =
                structureChanged
                        || !Objects.equals(variant.getThumbnailUrl(), form.getThumbnailUrl().trim())
                        || !Objects.equals(
                                variant.getLowStockThreshold(), form.getLowStockThreshold());
        BigDecimal previousPrice = variant.getPrice();
        BigDecimal previousCompareAtPrice = variant.getCompareAtPrice();
        boolean priceChanged = !sameDecimal(previousPrice, form.getPrice());
        boolean compareAtPriceChanged =
                !sameDecimal(previousCompareAtPrice, form.getCompareAtPrice());
        boolean reviewableVariantChange =
                structureChanged
                        || (otherFieldsChanged || priceChanged || compareAtPriceChanged)
                                && product.isModerationBlocked();
        if (reviewableVariantChange) {
            requestReapproval(product);
        }
        applyVariantFields(variant, form);
        productVariantRepository.save(variant);
        savePrimaryImage(productId, variantId, form.getThumbnailUrl());
        Long actorId = authService.getCurrentUserId(authentication);
        if (priceChanged || compareAtPriceChanged || otherFieldsChanged) {
            String auditReason = changeReason;
            if (priceChanged) {
                auditReason +=
                        "; giá bán "
                                + auditPrice(previousPrice)
                                + " → "
                                + auditPrice(variant.getPrice());
            }
            if (compareAtPriceChanged) {
                auditReason +=
                        "; giá trước giảm "
                                + auditPrice(previousCompareAtPrice)
                                + " → "
                                + auditPrice(variant.getCompareAtPrice());
            }
            if (structureChanged) {
                auditReason +=
                        "; thay đổi tên, khối lượng hoặc đơn vị bán; yêu cầu hậu kiểm thông tin mới.";
            }
            inventoryJournalService.audit(
                    actorId,
                    otherFieldsChanged ? "UPDATE" : "PRICE",
                    "VARIANT",
                    variantId,
                    auditReason);
        }
    }

    /**
     * Xóa mềm SKU và giữ nguyên các tham chiếu đơn hàng, lô và lịch sử.
     * Khóa product rồi SKU; tạm ẩn product trước khi xóa SKU đang bật cuối để thỏa điều kiện xuất bản.
     */
    @Override
    @Transactional
    public void deleteVariant(
            Authentication authentication, Long productId, Long variantId, String deletionReason) {
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        ProductVariant variant = getVariantForUpdate(productId, variantId);
        require(
                variant.getStatus() != VariantStatus.DELETED,
                "Phân loại đã được xóa hoặc ngừng kinh doanh.");
        String changeReason = reason(deletionReason);
        require(
                !hasReservedStock(variantId),
                "Không thể xóa phân loại còn số lượng chờ xuất kho. Hãy xử lý các đơn liên quan trước.");

        Long actorId = authService.getCurrentUserId(authentication);
        if (product.getStatus() == ProductStatus.ACTIVE
                && !hasOpenVariant(
                        productVariantRepository.findByProductIdOrderByVariantId(productId),
                        variantId)) {
            product.setStatus(ProductStatus.INACTIVE);
            product.setSellerHidden(true);
            product.setUpdatedAt(now());
            productRepository.saveAndFlush(product);
            inventoryJournalService.audit(
                    actorId,
                    "STATUS",
                    "PRODUCT",
                    productId,
                    "Mở bán → Tạm ẩn: xóa phân loại đang bật cuối cùng ("
                            + variant.getSku()
                            + "). "
                            + changeReason);
        }

        variant.setStatus(VariantStatus.DELETED);
        variant.setUpdatedAt(now());
        productVariantRepository.save(variant);
        inventoryJournalService.audit(
                actorId,
                "DELETE",
                "VARIANT",
                variantId,
                "Xóa phân loại " + variant.getSku() + ": " + changeReason);
    }

    /** Kiểm tra tên, đơn vị hoặc khối lượng có thay đổi để quyết định yêu cầu hậu kiểm. */
    private boolean variantStructureChanged(ProductVariant variant, VariantRequest form) {
        return !Objects.equals(variant.getVariantName(), form.getVariantName().trim())
                || !Objects.equals(variant.getUnit(), form.getUnit())
                || !sameDecimal(variant.getWeight(), form.getWeight());
    }

    /** So sánh giá trị số, bỏ qua khác biệt về số chữ số thập phân và xử lý cả null. */
    private boolean sameDecimal(BigDecimal current, BigDecimal requested) {
        if (current == null) {
            return requested == null;
        }
        return requested != null && current.compareTo(requested) == 0;
    }

    /** Định dạng giá bằng đồng cho lịch sử thao tác, kèm nhãn khi chưa thiết lập giá. */
    private String auditPrice(BigDecimal value) {
        return value == null ? "chưa thiết lập" : value.stripTrailingZeros().toPlainString() + " đ";
    }

    /** Đưa về chờ hậu kiểm và xóa thông tin người duyệt, thời điểm duyệt trước đó. */
    private void resetApproval(Product product) {
        product.setApprovalStatus(ApprovalStatus.PENDING);
        product.setApprovedBy(null);
        product.setApprovedAt(null);
    }

    /** Yêu cầu hậu kiểm nếu chưa chờ duyệt, giữ nguyên trạng thái bán và khóa của quản trị viên. */
    private void requestReapproval(Product product) {
        if (product.getApprovalStatus() == ApprovalStatus.PENDING) {
            return;
        }
        resetApproval(product);
        product.setUpdatedAt(now());
        // Yêu cầu hậu kiểm không thay đổi trạng thái kinh doanh hay khóa của quản trị viên.
        productRepository.saveAndFlush(product);
    }

    /**
     * Đổi trạng thái phân loại; lựa chọn xóa dùng chung quy tắc xóa từ menu thao tác.
     * Khi tạm ẩn, sản phẩm đang mở bán phải còn ít nhất một phân loại mở bán.
     */
    @Override
    @Transactional
    public void changeVariantStatus(
            Authentication authentication,
            Long productId,
            Long variantId,
            VariantActionRequest form) {
        if (form.getStatus() == VariantStatus.DELETED) {
            deleteVariant(authentication, productId, variantId, form.getReason());
            return;
        }
        Product product = getOwnedProductForUpdate(authentication, productId);
        requireWritableProduct(product);
        ProductVariant variant = getVariantForUpdate(productId, variantId);
        require(variant.getStatus() != VariantStatus.DELETED, "Phân loại đã ngừng kinh doanh.");
        VariantStatus requestedStatus = form.getStatus();
        require(
                requestedStatus == VariantStatus.ACTIVE
                        || requestedStatus == VariantStatus.INACTIVE,
                "Chọn Mở bán, Tạm ẩn hoặc Xóa phân loại.");
        String changeReason = reason(form.getReason());
        if (requestedStatus != VariantStatus.ACTIVE
                && product.getStatus() == ProductStatus.ACTIVE) {
            // Bỏ qua chính SKU đang đổi để kiểm tra sản phẩm vẫn còn SKU khác đang bật.
            List<ProductVariant> variants =
                    productVariantRepository.findByProductIdOrderByVariantId(productId);
            require(
                    hasOpenVariant(variants, variantId),
                    "Tạm ẩn sản phẩm trước khi tạm ẩn phân loại mở bán cuối cùng.");
        }
        VariantStatus previousStatus = variant.getStatus();
        variant.setStatus(requestedStatus);
        variant.setUpdatedAt(now());
        productVariantRepository.save(variant);
        if (previousStatus != requestedStatus) {
            inventoryJournalService.audit(
                    authService.getCurrentUserId(authentication),
                    "STATUS",
                    "VARIANT",
                    variantId,
                    variantStatusLabel(previousStatus)
                            + " → "
                            + variantStatusLabel(requestedStatus)
                            + ": "
                            + changeReason);
        }
    }

    /** Chuyển trạng thái sản phẩm thành nhãn tiếng Việt để ghi lịch sử thay đổi. */
    private String productStatusLabel(ProductStatus status) {
        return switch (status) {
            case ACTIVE -> "Mở bán";
            case INACTIVE -> "Tạm ẩn";
            case DELETED -> "Đã xóa";
        };
    }

    /** Chuyển trạng thái phân loại thành nhãn tiếng Việt để ghi lịch sử thay đổi. */
    private String variantStatusLabel(VariantStatus status) {
        return switch (status) {
            case ACTIVE -> "Mở bán";
            case INACTIVE -> "Tạm ẩn";
            case OUT_OF_STOCK -> "Mở bán (hết hàng)";
            case DELETED -> "Xóa phân loại";
        };
    }

    /** Tạo biểu mẫu sửa từ sản phẩm đã kiểm tra quyền, kèm danh mục và ảnh hiện tại. */
    @Override
    public ProductRequest editForm(Authentication authentication, Long productId) {
        ProductResponse response = get(authentication, productId);
        Product product = response.getProduct();
        ProductRequest form = new ProductRequest();
        form.setName(product.getName());
        form.setDescription(product.getDescription());
        form.setOrigin(product.getOrigin());
        form.setExpiryWarningDays(product.getExpiryWarningDays());
        form.setImageUrl(response.getImageUrl());
        form.setRequiresPreparationCheck(
                Boolean.TRUE.equals(product.getRequiresPreparationCheck()));
        form.setPreparationNote(product.getPreparationNote());
        List<ProductCategory> links = productCategoryRepository.findByIdProductId(productId);
        if (!links.isEmpty()) {
            form.setCategoryId(links.getFirst().getId().getCategoryId());
        }
        return form;
    }

    /** Tạo biểu mẫu sửa phân loại từ dữ liệu hiện tại, không nhập lại tồn kho ban đầu. */
    @Override
    public VariantRequest variantForm(
            Authentication authentication, Long productId, Long variantId) {
        ProductVariant variant = getVariant(authentication, productId, variantId).getVariant();
        VariantRequest form = new VariantRequest();
        form.setSku(variant.getSku());
        form.setVariantName(variant.getVariantName());
        form.setThumbnailUrl(variant.getThumbnailUrl());
        form.setPrice(variant.getPrice());
        form.setCompareAtPrice(variant.getCompareAtPrice());
        form.setWeight(variant.getWeight());
        form.setUnit(variant.getUnit());
        form.setLowStockThreshold(variant.getLowStockThreshold());
        form.setNoInitialStock(true);
        return form;
    }
}
