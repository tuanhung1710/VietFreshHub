package VietFreshHub.product.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.nearExpiry;
import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.reason;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.MarkdownStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.BatchMarkdownRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.BulkProductActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.CatalogSummary;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.PriceHistory;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductCategory;
import VietFreshHub.product.entity.ProductImage;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.PriceHistoryRepository;
import VietFreshHub.product.repository.ProductCategoryRepository;
import VietFreshHub.product.repository.ProductImageRepository;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.product.validator.CategoryValidator;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.PageSizes;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final List<String> SALE_UNITS = List.of("kg", "hộp", "túi", "quả", "chùm");

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final BatchMarkdownRepository batchMarkdownRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StockAllocationRepository stockAllocationRepository;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final ShopService shopService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final CategoryValidator categoryValidator;
    private final ProductAvailabilityService productAvailabilityService;

    @Override
    public Page<ProductResponse> searchInventory(Authentication authentication,
            VietFreshHub.inventory.dto.InventoryCatalogFilter filters, int page, int size) {
        Long shop = shopService.getManagedShopId(authentication);
        filters.setQ(normalizeFilter(filters.getQ()));
        filters.setStock(normalizeFilter(filters.getStock()));
        filters.setExpiry(normalizeFilter(filters.getExpiry()));
        // Compatibility for existing dashboard/bookmark links, with visible expiry scope.
        if (List.of("near", "expired", "expiry").contains(filters.getStock())) {
            filters.setExpiry(filters.getStock().equals("expiry") ? "attention" : filters.getStock());
            filters.setStock("");
        } else if (filters.getStock().equals("archived")) {
            filters.setArchived(true);
            filters.setStock("");
        }
        require(List.of("", "available", "low", "empty").contains(filters.getStock()), "Mức tồn kho không hợp lệ.");
        require(List.of("", "valid", "near", "expired", "attention").contains(filters.getExpiry()), "Hạn sử dụng không hợp lệ.");
        if (filters.getVariantId() != null) {
            ProductVariant variant = productVariantRepository.findById(filters.getVariantId())
                    .orElseThrow(() -> new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn."));
            Product parent = getOwnedProduct(authentication, variant.getProductId(), false);
            if (parent.getStatus() == ProductStatus.DELETED || variant.getStatus() == VariantStatus.DELETED) {
                filters.setArchived(true);
            }
        }
        int pageSize = PageSizes.normalize(size);
        PageRequest request = PageRequest.of(Math.min(Math.max(1, page)-1, Integer.MAX_VALUE/pageSize), pageSize);
        Page<Product> products = productRepository.searchInventory(shop, filters, today(), request);
        if (products.getNumber() >= Math.max(1, products.getTotalPages())) {
            products = productRepository.searchInventory(shop, filters, today(), request.withPage(0));
        }
        List<Long> ids = products.getContent().stream().map(Product::getProductId).toList();
        Set<Long> matched = new HashSet<>(productRepository.matchingInventoryVariants(shop, filters, today(), ids));
        Page<ProductResponse> result = catalogResponses(products);
        for (ProductResponse row : result) {
            row.setInventoryVariantTotal((int)row.getVariants().stream()
                    .filter(v -> filters.isArchived() || v.getVariant().getStatus() != VariantStatus.DELETED).count());
            List<VariantResponse> children = row.getVariants().stream()
                    .filter(v -> matched.contains(v.getVariant().getVariantId())).toList();
            row.setInventoryVariants(children);
            for (VariantResponse child : children) {
                if (!filters.getExpiry().isEmpty() || BusinessCodes.parse("LOT", filters.getQ()) != null) {
                    child.setMatchedBatches(child.getBatches().stream()
                            .filter(b -> inventoryBatchMatches(b, filters)).toList());
                }
            }
            row.setBatchCount(children.stream().mapToInt(v -> v.getInventoryBatches().size()).sum());
            row.setExpired(children.stream().flatMap(v -> v.getInventoryBatches().stream())
                    .anyMatch(b -> b.getBatch().getQuantityOnHand() > 0 && b.isExpired()));
            row.setNearExpiry(children.stream().flatMap(v -> v.getInventoryBatches().stream())
                    .anyMatch(b -> b.getBatch().getQuantityOnHand() > 0 && b.isNearExpiry()));
        }
        return result;
    }

    private boolean inventoryBatchMatches(BatchResponse b, VietFreshHub.inventory.dto.InventoryCatalogFilter filters) {
        Long batchCode = BusinessCodes.parse("LOT", filters.getQ());
        if (batchCode != null && !batchCode.equals(b.getBatch().getBatchId())) return false;
        return filters.getExpiry().isEmpty() || b.getBatch().getQuantityOnHand() > 0 && switch (filters.getExpiry()) {
            case "near" -> b.isNearExpiry();
            case "expired" -> b.isExpired();
            case "valid" -> !b.isExpired() && !b.isNearExpiry();
            case "attention" -> b.isExpired() || b.isNearExpiry();
            default -> false;
        };
    }

    @Override
    public Page<ProductResponse> searchManagement(Authentication authentication, String query, Long categoryId,
            String tab, LocalDate createdFrom, LocalDate createdTo, int page, int size) {
        ProductCatalogFilter filters = new ProductCatalogFilter();
        filters.setQ(query);
        filters.setCategory(categoryId);
        filters.setTab(tab);
        filters.setCreatedFrom(createdFrom);
        filters.setCreatedTo(createdTo);
        return searchManagement(authentication, filters, page, size);
    }

    @Override
    public Page<ProductResponse> searchManagement(Authentication authentication, ProductCatalogFilter filters, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        filters.setQ(normalizeFilter(filters.getQ()));
        filters.setTab(normalizeFilter(filters.getTab()));
        filters.setStock(normalizeFilter(filters.getStock()));
        filters.setSort(normalizeFilter(filters.getSort()));
        // Retired UI filter: old bookmarked approval parameters must not silently filter the list.
        filters.setApproval("");
        filters.setEligibility(normalizeFilter(filters.getEligibility()));
        if (filters.getSort().isEmpty()) filters.setSort("updated_desc");
        require(List.of("", "active", "out-of-stock", "violation", "hidden").contains(filters.getTab()), "Tab sản phẩm không hợp lệ.");
        require(List.of("", "available", "empty", "low", "expiry").contains(filters.getStock()), "Bộ lọc tồn kho không hợp lệ.");
        require(List.of("updated_desc", "updated_asc").contains(filters.getSort()), "Cách sắp xếp không hợp lệ.");
        require(List.of("", "PENDING", "APPROVED", "REJECTED").contains(filters.getApproval()), "Trạng thái kiểm tra không hợp lệ.");
        require(List.of("", "unavailable").contains(filters.getEligibility()), "Điều kiện đặt mua không hợp lệ.");
        LocalDate createdFrom = filters.getCreatedFrom();
        LocalDate createdTo = filters.getCreatedTo();
        require(createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo), "Ngày bắt đầu không được sau ngày kết thúc.");
        for (BigDecimal price : new BigDecimal[] {filters.getMinPrice(), filters.getMaxPrice()}) {
            require(price == null || (price.signum() >= 0 && price.stripTrailingZeros().scale() <= 0
                    && price.precision() - price.scale() <= 16), "Giá lọc phải là số nguyên đồng không âm, tối đa 16 chữ số.");
        }
        require(filters.getMinPrice() == null || filters.getMaxPrice() == null
                || filters.getMinPrice().compareTo(filters.getMaxPrice()) <= 0, "Giá từ không được lớn hơn giá đến.");
        List<Long> activeCategories = productAvailabilityService.activeCategoryIds();
        int pageSize = PageSizes.normalize(size);
        PageRequest pageable = PageRequest.of(Math.min(Math.max(1, page) - 1, Integer.MAX_VALUE / pageSize), pageSize);
        Page<Product> result = productRepository.searchManagement(shopId, filters,
                startOfDayOrNull(createdFrom), startOfNextDayOrNull(createdTo), activeCategories, today(), pageable);
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            // Invalid/outdated pages return to the first page, including after a filter change.
            result = productRepository.searchManagement(shopId, filters,
                    startOfDayOrNull(createdFrom), startOfNextDayOrNull(createdTo), activeCategories, today(), pageable.withPage(0));
        }
        return catalogResponses(result);
    }

    @Override
    public ProductTabSummary productTabSummary(Authentication authentication) {
        return productRepository.managementSummary(shopService.getManagedShopId(authentication),
                productAvailabilityService.activeCategoryIds(), today());
    }

    @Override
    public VietFreshHub.inventory.dto.InventoryAttentionSummary inventoryAttentionSummary(Authentication authentication) {
        return productRepository.inventoryAttentionSummary(shopService.getManagedShopId(authentication), today());
    }

    @Override
    public Page<ProductResponse> search(
            Authentication authentication,
            String query,
            Long categoryId,
            String status,
            boolean archived,
            String stock,
            int page,
            int size) {
        return search(
                authentication,
                query,
                categoryId,
                status,
                archived,
                stock,
                "",
                "",
                null,
                null,
                page,
                size);
    }

    @Override
    public Page<ProductResponse> search(
            Authentication authentication,
            String query,
            Long categoryId,
            String status,
            boolean archived,
            String stock,
            String business,
            String approval,
            LocalDate createdFrom,
            LocalDate createdTo,
            int page,
            int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        String statusFilter = normalizeFilter(status);
        String approvalFilter = normalizeFilter(approval);
        String stockFilter = normalizeFilter(stock);
        String businessFilter = normalizeFilter(business);
        ProductStatus productStatus = findProductStatus(statusFilter);
        ApprovalStatus statusApproval = findApprovalStatus(statusFilter);
        ApprovalStatus approvalStatus = findApprovalStatus(approvalFilter);
        validateFilters(
                statusFilter,
                productStatus,
                statusApproval,
                stockFilter,
                businessFilter,
                approvalFilter,
                approvalStatus,
                createdFrom,
                createdTo);

        // Compatibility for service callers using the old business filter names.
        if (!businessFilter.isEmpty()) {
            return searchManagement(authentication, query, categoryId,
                    businessFilter.equals("published") ? "active" : "hidden",
                    createdFrom, createdTo, page, size);
        }
        boolean includeArchived =
                archived
                        || stockFilter.equals("archived")
                        || productStatus == ProductStatus.DELETED;
        LocalDate date = today();
        PageRequest pageable = catalogPageRequest(page, size);
        Page<Product> result =
                productRepository.search(
                        shopId,
                        normalizeFilter(query),
                        BusinessCodes.parse("PRD", query), BusinessCodes.parse("LOT", query),
                        categoryId,
                        productStatus,
                        statusApproval,
                        includeArchived,
                        stockFilter,
                        approvalStatus,
                        startOfDayOrNull(createdFrom),
                        startOfNextDayOrNull(createdTo),
                        date,
                        pageable);
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            pageable = pageable.withPage(Math.max(0, result.getTotalPages() - 1));
            result =
                    productRepository.search(
                            shopId,
                            normalizeFilter(query),
                        BusinessCodes.parse("PRD", query), BusinessCodes.parse("LOT", query),
                            categoryId,
                            productStatus,
                            statusApproval,
                            includeArchived,
                            stockFilter,
                            approvalStatus,
                            startOfDayOrNull(createdFrom),
                            startOfNextDayOrNull(createdTo),
                                date,
                            pageable);
        }
        return catalogResponses(result);
    }

    private void validateFilters(
            String status,
            ProductStatus productStatus,
            ApprovalStatus statusApproval,
            String stock,
            String business,
            String approval,
            ApprovalStatus approvalStatus,
            LocalDate createdFrom,
            LocalDate createdTo) {
        require(
                status.isEmpty() || productStatus != null || statusApproval != null,
                "Trạng thái sản phẩm được chọn không hợp lệ.");
        require(
                List.of("", "low", "near", "expired", "expiry", "archived").contains(stock),
                "Bộ lọc tồn kho không hợp lệ.");
        require(
                List.of("", "published", "unpublished").contains(business),
                "Trạng thái kinh doanh được chọn không hợp lệ.");
        require(approval.isEmpty() || approvalStatus != null, "Trạng thái kiểm duyệt được chọn không hợp lệ.");
        require(
                createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo),
                "Ngày bắt đầu không được sau ngày kết thúc.");
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private ProductStatus findProductStatus(String value) {
        for (ProductStatus status : ProductStatus.values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }

    private ApprovalStatus findApprovalStatus(String value) {
        for (ApprovalStatus status : ApprovalStatus.values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }

    private LocalDateTime startOfDayOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        return InventoryRules.startOfDayUtc(date);
    }

    private LocalDateTime startOfNextDayOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        return InventoryRules.startOfDayUtc(date.plusDays(1));
    }

    private PageRequest catalogPageRequest(int page, int size) {
        int pageSize = PageSizes.normalize(size);
        Sort sort = Sort.by(Sort.Direction.DESC, "productId");
        int pageIndex = Math.min(Math.max(1, page) - 1, Integer.MAX_VALUE / pageSize);
        return PageRequest.of(pageIndex, pageSize, sort);
    }

    private Page<ProductResponse> catalogResponses(Page<Product> products) {
        List<Long> productIds = new ArrayList<>();
        for (Product product : products.getContent()) {
            productIds.add(product.getProductId());
        }
        List<ProductVariant> variants = List.of();
        if (!productIds.isEmpty()) {
            variants = productVariantRepository.findByProductIdInOrderByVariantId(productIds);
        }
        List<ProductVariant> pageVariants = variants;
        List<InventoryBatch> batches = findBatches(variants);
        Map<Long, ProductManagementState> states = products.isEmpty() ? Map.of()
                : productAvailabilityService.states(products.getContent().getFirst().getShopId(), productIds);
        return products.map(
                product -> {
                    List<ProductVariant> productVariants = new ArrayList<>();
                    for (ProductVariant variant : pageVariants) {
                        if (variant.getProductId().equals(product.getProductId())) {
                            productVariants.add(variant);
                        }
                    }
                    return buildProductResponse(product, productVariants, batches, false, states.get(product.getProductId()));
                });
    }

    @Override
    public CatalogSummary catalogSummary(Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        long productCount =
                productRepository.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED);
        long variantCount = productVariantRepository.countVisibleInShop(shopId);
        return new CatalogSummary(productCount, variantCount);
    }

    @Override
    public ProductResponse get(Authentication authentication, Long productId) {
        Product product = getOwnedProduct(authentication, productId, false);
        List<ProductVariant> variants =
                productVariantRepository.findByProductIdOrderByVariantId(productId);
        ProductManagementState state = productAvailabilityService.states(product.getShopId(), List.of(productId)).get(productId);
        return buildProductResponse(product, variants, findBatches(variants), true, state);
    }

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

    private List<InventoryBatch> findBatches(List<ProductVariant> variants) {
        List<Long> variantIds = new ArrayList<>();
        for (ProductVariant variant : variants) {
            variantIds.add(variant.getVariantId());
        }
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return inventoryBatchRepository.findByVariantIdInOrderByExpiryDateAscBatchIdAsc(variantIds);
    }

    private ProductResponse buildProductResponse(
            Product product,
            List<ProductVariant> variants,
            List<InventoryBatch> batches,
            boolean details,
            ProductManagementState state) {
        LocalDate date = today();
        List<BatchMarkdown> markdowns = findActiveMarkdowns(batches);
        List<VariantResponse> variantRows = new ArrayList<>();
        for (ProductVariant variant : variants) {
            boolean sellable =
                    state != null && state.code().equals("active")
                            && variant.getStatus() == VariantStatus.ACTIVE;
            variantRows.add(
                    buildVariantResponse(
                            product, variant, batches, markdowns, details, sellable, date));
        }

        ProductResponse response = new ProductResponse();
        response.setProduct(product);
        response.setImageUrl(productImageUrl(product.getProductId()));
        List<String> categoryNames = categoryNames(product.getProductId());
        response.setCategoryNames(categoryNames);
        response.setCategoryName(String.join(", ", categoryNames));
        response.setVariants(variantRows);
        response.setApprovedByName(approvedByName(product.getApprovedBy()));
        if (state == null) state = new ProductManagementState("archived", "Sản phẩm đã ngừng kinh doanh.");
        response.setManagementState(state);
        response.setPublished(List.of("active", "out-of-stock").contains(state.code()));
        populateCatalogSummary(response);
        return response;
    }

    private VariantResponse buildVariantResponse(
            Product product,
            ProductVariant variant,
            List<InventoryBatch> batches,
            List<BatchMarkdown> markdowns,
            boolean details,
            boolean sellable,
            LocalDate date) {
        List<BatchResponse> batchRows = new ArrayList<>();
        long physicalQuantity = 0;
        long reservedQuantity = 0;
        long availableQuantity = 0;
        boolean hasExpiredStock = false;
        boolean hasNearExpiryStock = false;
        BigDecimal offerPrice = null;

        for (InventoryBatch batch : batches) {
            if (!batch.getVariantId().equals(variant.getVariantId())) {
                continue;
            }
            BatchResponse row =
                    buildBatchResponse(product, variant, batch, markdowns, details, date);
            batchRows.add(row);
            physicalQuantity += batch.getQuantityOnHand();
            reservedQuantity += batch.getReservedQuantity();
            availableQuantity += row.getAvailable();
            if (batch.getQuantityOnHand() > 0) {
                hasExpiredStock = hasExpiredStock || row.isExpired();
                hasNearExpiryStock = hasNearExpiryStock || row.isNearExpiry();
            }
            if (sellable && row.getMarkdown() != null) {
                offerPrice = lowerPrice(offerPrice, row.getMarkdown().getPrice());
            }
        }

        int lowStockThreshold = 0;
        if (variant.getLowStockThreshold() != null) {
            lowStockThreshold = variant.getLowStockThreshold();
        }
        VariantResponse response = new VariantResponse();
        response.setVariant(variant);
        response.setProductName(product.getName());
        response.setPhysical(physicalQuantity);
        response.setReserved(reservedQuantity);
        response.setAvailable(availableQuantity);
        response.setUnavailable(physicalQuantity - reservedQuantity - availableQuantity);
        response.setBatches(batchRows);
        response.setOfferPrice(offerPrice);
        response.setOfferQuantity(offerQuantity(batchRows, offerPrice));
        response.setLowStock(availableQuantity > 0 && availableQuantity <= lowStockThreshold);
        response.setBatchCount(batchRows.size());
        response.setExpired(hasExpiredStock);
        response.setNearExpiry(hasNearExpiryStock);
        return response;
    }

    private BatchResponse buildBatchResponse(
            Product product,
            ProductVariant variant,
            InventoryBatch batch,
            List<BatchMarkdown> markdowns,
            boolean details,
            LocalDate date) {
        BatchMarkdown currentMarkdown = null;
        for (BatchMarkdown markdown : markdowns) {
            if (InventoryRules.isApplicableMarkdown(batch, markdown, variant.getPrice(), date)) {
                currentMarkdown = markdown;
                break;
            }
        }
        List<VietFreshHub.inventory.entity.StockAllocation> allocations = List.of();
        if (details) {
            allocations =
                    stockAllocationRepository.findByBatchIdOrderByAllocationIdDesc(
                            batch.getBatchId());
        }
        return new BatchResponse(
                batch,
                available(batch, date),
                expired(batch, date),
                nearExpiry(batch, date, product.getExpiryWarningDays()),
                currentMarkdown,
                allocations,
                product.getName(),
                variant.getSku(),
                variant.getUnit(),
                InventoryRules.markdownEligible(batch, date),
                product.getExpiryWarningDays(),
                InventoryRules.daysUntilExpiry(batch, date));
    }

    private List<BatchMarkdown> findActiveMarkdowns(List<InventoryBatch> batches) {
        List<Long> batchIds = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            batchIds.add(batch.getBatchId());
        }
        if (batchIds.isEmpty()) {
            return List.of();
        }
        return batchMarkdownRepository.findByBatchIdInAndStatusOrderByMarkdownIdDesc(
                batchIds, MarkdownStatus.ACTIVE);
    }

    private long offerQuantity(List<BatchResponse> batches, BigDecimal offerPrice) {
        if (offerPrice == null) {
            return 0;
        }
        long quantity = 0;
        for (BatchResponse batch : batches) {
            BatchMarkdown markdown = batch.getMarkdown();
            if (markdown != null && markdown.getPrice().compareTo(offerPrice) == 0) {
                quantity += batch.getAvailable();
            }
        }
        return quantity;
    }

    private void populateCatalogSummary(ProductResponse response) {
        List<VariantResponse> visibleVariants = new ArrayList<>();
        List<VariantResponse> inventoryVariants = new ArrayList<>();
        int batchCount = 0;
        for (VariantResponse row : response.getVariants()) {
            ProductVariant variant = row.getVariant();
            if (variant.getStatus() != VariantStatus.DELETED) {
                visibleVariants.add(row);
                response.setMinPrice(lowerPrice(response.getMinPrice(), variant.getPrice()));
                response.setMaxPrice(higherPrice(response.getMaxPrice(), variant.getPrice()));
                response.setMinComparePrice(
                        lowerPrice(response.getMinComparePrice(), variant.getCompareAtPrice()));
                response.setMaxComparePrice(
                        higherPrice(response.getMaxComparePrice(), variant.getCompareAtPrice()));
            }
            if (variant.getStatus() != VariantStatus.DELETED
                    || row.getPhysical() > 0
                    || row.getReserved() > 0) {
                inventoryVariants.add(row);
                batchCount += row.getBatchCount();
                response.setExpired(response.isExpired() || row.isExpired());
                response.setNearExpiry(response.isNearExpiry() || row.isNearExpiry());
            }
        }
        response.setVisibleVariants(visibleVariants);
        response.setInventoryVariants(inventoryVariants);
        response.setVariantCount(visibleVariants.size());
        response.setBatchCount(batchCount);
    }

    private BigDecimal lowerPrice(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.compareTo(current) < 0) {
            return candidate;
        }
        return current;
    }

    private BigDecimal higherPrice(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.compareTo(current) > 0) {
            return candidate;
        }
        return current;
    }

    private List<String> categoryNames(Long productId) {
        List<String> names = new ArrayList<>();
        for (ProductCategory link : productCategoryRepository.findByIdProductId(productId)) {
            Category category =
                    categoryRepository.findById(link.getId().getCategoryId()).orElse(null);
            if (category == null) {
                names.add("Danh mục không còn tồn tại");
                continue;
            }
            names.add(category.getName());
        }
        return names;
    }

    private String productImageUrl(Long productId) {
        for (ProductImage image :
                productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId)) {
            if (image.getVariantId() == null) {
                return image.getImageUrl();
            }
        }
        return null;
    }

    private String approvedByName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        return user.getFullName();
    }

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

    @Override
    public List<String> units() {
        return SALE_UNITS;
    }

    private Product getOwnedProduct(Authentication authentication, Long productId, boolean lock) {
        Long shopId = shopService.getManagedShopId(authentication);
        if (lock) {
            Product product =
                    productRepository
                            .lockOwned(productId, shopId)
                            .orElseThrow(
                                    () ->
                                            new AccessDeniedException(
                                                    "Sản phẩm không thuộc cửa hàng của bạn."));
            return inventoryLockHelper.refresh(product);
        }
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

    private void requireWritableProduct(Product product) {
        require(
                product.getStatus() != ProductStatus.DELETED,
                "Sản phẩm đã ngừng kinh doanh. Bạn chỉ có thể xem lịch sử và xử lý tồn kho.");
    }

    private void validateProductFields(ProductRequest form) {
        String name = form.getName();
        require(
                name != null && !name.isBlank() && name.trim().length() <= 250,
                "Nhập tên sản phẩm, tối đa 250 ký tự.");
        require(
                form.getCategoryId() != null
                        && categoryValidator.isActiveCategoryChain(form.getCategoryId()),
                "Chọn danh mục còn hoạt động. Các danh mục cấp trên cũng phải còn hoạt động.");
        validateImageUrl(form.getImageUrl());
        require(
                form.getExpiryWarningDays() != null && form.getExpiryWarningDays() >= 0,
                "Số ngày nhắc trước hạn sử dụng phải là số nguyên không âm.");
        if (form.isRequiresPreparationCheck()) {
            require(
                    form.getPreparationNote() != null && !form.getPreparationNote().isBlank(),
                    "Nhập hướng dẫn kiểm tra và đóng gói.");
        }
    }

    private void validateImageUrl(String url) {
        boolean valid = url != null && !url.isBlank() && url.length() <= 1000;
        if (valid) {
            valid =
                    url.startsWith("https://")
                            || url.startsWith("http://")
                            || url.startsWith("/images/");
        }
        require(valid, "Dán liên kết ảnh bắt đầu bằng http:// hoặc https://, hoặc tải ảnh lên.");
    }

    private void validateVariantFields(VariantRequest form, ProductVariant existing) {
        if (form.getSku() != null && !form.getSku().isBlank()) {
            require(form.getSku().matches("[A-Za-z0-9_-]{1,100}"),
                    "SKU chỉ được chứa chữ cái không dấu, chữ số, dấu gạch nối (-) và dấu gạch dưới (_).");
            if (existing == null) {
                require(!form.getSku().matches("(?i)(SKU-[0-9]+|TMP-.*)"),
                        "Để trống SKU để cấp tự động; mã SKU- và TMP- được dành cho hệ thống.");
            }
            if (existing == null || !existing.getSku().equalsIgnoreCase(form.getSku())) {
                require(!productVariantRepository.existsBySkuIgnoreCase(form.getSku()), "SKU đã tồn tại.");
            }
        }
        require(
                form.getVariantName() != null
                        && !form.getVariantName().isBlank()
                        && form.getVariantName().length() <= 200,
                "Nhập tên phân loại.");
        InventoryRules.price(form.getPrice());
        validateImageUrl(form.getThumbnailUrl());
        boolean supportedUnit = SALE_UNITS.contains(form.getUnit());
        boolean unchangedUnit =
                existing != null && Objects.equals(existing.getUnit(), form.getUnit());
        require(supportedUnit || unchangedUnit, "Chọn đơn vị bán hợp lệ.");
        require(
                form.getLowStockThreshold() != null && form.getLowStockThreshold() >= 0,
                "Mức tồn kho cảnh báo phải lớn hơn hoặc bằng 0.");
        require(
                form.getCompareAtPrice() == null
                        || form.getCompareAtPrice().compareTo(form.getPrice()) >= 0,
                "Giá trước giảm phải lớn hơn hoặc bằng giá bán.");
        require(
                form.getWeight() == null
                        || (form.getWeight().compareTo(new BigDecimal("0.001")) >= 0
                                && form.getWeight().stripTrailingZeros().scale() <= 3),
                "Giá trị phải từ 0,001 kg, tối đa 3 chữ số thập phân.");
        if (existing != null && !Objects.equals(existing.getUnit(), form.getUnit())) {
            require(
                    !hasStockOrHistory(existing.getVariantId()),
                    "Không thể đổi đơn vị bán của phân loại đã có tồn kho hoặc lịch sử. Hãy tạo phân loại mới.");
        }
    }

    private boolean hasStockOrHistory(Long variantId) {
        if (inventoryTransactionRepository.existsByVariantId(variantId)
                || productVariantRepository.orderReferenceCount(variantId) > 0) {
            return true;
        }
        for (InventoryBatch batch :
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId)) {
            if (batch.getQuantityOnHand() > 0 || batch.getReservedQuantity() > 0) {
                return true;
            }
        }
        return false;
    }

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

    private void applyProductFields(Product product, ProductRequest form) {
        product.setName(form.getName().trim());
        product.setDescription(form.getDescription());
        product.setExpiryWarningDays(form.getExpiryWarningDays());
        product.setRequiresPreparationCheck(form.isRequiresPreparationCheck());
        product.setPreparationNote(null);
        if (form.isRequiresPreparationCheck()) {
            product.setPreparationNote(form.getPreparationNote().trim());
        }
        product.setUpdatedAt(now());
    }

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

    private void savePrimaryImage(Long productId, Long variantId, String url) {
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

    private void recordPriceChange(ProductVariant variant, Long actorId) {
        LocalDateTime effectiveAt = now();
        for (PriceHistory history :
                priceHistoryRepository.findByVariantIdAndEffectiveToIsNull(
                        variant.getVariantId())) {
            if (!effectiveAt.isAfter(history.getEffectiveFrom())) {
                effectiveAt = history.getEffectiveFrom().plusSeconds(1);
            }
            history.setEffectiveTo(effectiveAt);
            priceHistoryRepository.save(history);
        }
        PriceHistory history = new PriceHistory();
        history.setVariantId(variant.getVariantId());
        history.setPrice(variant.getPrice());
        history.setEffectiveFrom(effectiveAt);
        history.setChangedBy(actorId);
        priceHistoryRepository.save(history);
    }

    @Override
    @Transactional
    public Long create(Authentication authentication, ProductRequest form) {
        Long shopId = shopService.getManagedShopId(authentication);
        Long actorId = authService.getCurrentUserId(authentication);
        validateProductFields(form);
        validateNewVariants(form);

        Product product = new Product();
        product.setShopId(shopId);
        applyProductFields(product, form);
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
        // Create the category and SKUs before activation to retain publication trigger guards.
        product.setSellerHidden(false);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
        inventoryJournalService.audit(
                actorId, "CREATE", "PRODUCT", product.getProductId(), "Tạo sản phẩm; mở bán và chờ hậu kiểm.");
        return product.getProductId();
    }

    private void validateNewVariants(ProductRequest form) {
        require(
                form.getVariants() != null
                        && !form.getVariants().isEmpty()
                        && form.getVariants().size() <= 20,
                "Mỗi sản phẩm cần từ 1 đến 20 phân loại.");
        for (VariantRequest variant : form.getVariants()) {
            // New SKUs are assigned by the server after IDENTITY is available.
            variant.setSku(null);
            validateVariantFields(variant, null);
            initialQuantity(variant);
        }
    }

    private int initialQuantity(VariantRequest form) {
        if (form.isNoInitialStock()) {
            require(
                    form.getInitialQuantity() == null || form.getInitialQuantity() == 0,
                    "Khi chọn Chưa nhập hàng, số lượng nhập ban đầu phải bằng 0.");
            return 0;
        }
        require(
                form.getInitialQuantity() != null && form.getInitialQuantity() > 0,
                "Nhập số lượng lớn hơn 0 hoặc chọn Chưa nhập hàng.");
        form.setExpiryDate(InventoryRules.resolveExpiryDate(
                form.getReceivedDate(), form.getShelfLifeDays(), form.getExpiryDate()));
        InventoryRules.dates(form.getReceivedDate(), form.getExpiryDate(), today());
        require(
                form.getCostPrice() == null || form.getCostPrice().signum() >= 0,
                "Giá vốn không được âm.");
        return form.getInitialQuantity();
    }

    private void createVariant(Product product, VariantRequest form, Long actorId) {
        int quantity = initialQuantity(form);
        ProductVariant variant = new ProductVariant();
        variant.setProductId(product.getProductId());
        applyVariantFields(variant, form);
        // Both writes are in this transaction; the temporary SKU is never committed.
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
        recordPriceChange(variant, actorId);
        if (quantity > 0) {
            createInitialBatch(variant, form, quantity, actorId);
        }
    }

    private void createInitialBatch(
            ProductVariant variant, VariantRequest form, int quantity, Long actorId) {
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

    @Override
    @Transactional
    public void update(Authentication authentication, Long productId, ProductRequest form) {
        Product product = getOwnedProduct(authentication, productId, true);
        requireWritableProduct(product);
        validateProductFields(form);
        String changeReason = reason(form.getReason());
        boolean metadataChanged = productMetadataChanged(product, form);
        Integer previousReminderDays = product.getExpiryWarningDays();
        boolean reminderChanged = !Objects.equals(previousReminderDays, form.getExpiryWarningDays());
        applyProductFields(product, form);
        if (!metadataChanged) {
            productRepository.saveAndFlush(product);
        } else {
            ProductStatus businessStatus = product.getStatus();
            resetApproval(product);
            // Temporarily suspend publication within this transaction while replacing categories.
            // Restore the seller's choice before commit; admin locks are never cleared.
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
        if (metadataChanged || reminderChanged) {
            String auditReason = changeReason;
            if (reminderChanged) {
                auditReason += "; nhắc trước hạn sử dụng " + previousReminderDays
                        + " → " + form.getExpiryWarningDays() + " ngày.";
            }
            inventoryJournalService.audit(
                    authService.getCurrentUserId(authentication),
                    metadataChanged ? "UPDATE" : "REMINDER",
                    "PRODUCT",
                    productId,
                    auditReason);
        }
    }

    private boolean productMetadataChanged(Product product, ProductRequest form) {
        List<ProductCategory> categories =
                productCategoryRepository.findByIdProductId(product.getProductId());
        Long category = categories.isEmpty() ? null : categories.getFirst().getId().getCategoryId();
        String preparationNote =
                form.isRequiresPreparationCheck() ? form.getPreparationNote().trim() : null;
        String previousPreparationNote = Boolean.TRUE.equals(product.getRequiresPreparationCheck())
                ? product.getPreparationNote() : null;
        return !Objects.equals(product.getName(), form.getName().trim())
                || !Objects.equals(emptyToNull(product.getDescription()), emptyToNull(form.getDescription()))
                || !Objects.equals(category, form.getCategoryId())
                || !Objects.equals(productImageUrl(product.getProductId()), form.getImageUrl().trim())
                || Boolean.TRUE.equals(product.getRequiresPreparationCheck()) != form.isRequiresPreparationCheck()
                || !Objects.equals(previousPreparationNote, preparationNote);
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    @Transactional
    public void changeStatuses(Authentication authentication, BulkProductActionRequest form) {
        require(form.getProductIds() != null && !form.getProductIds().isEmpty() && form.getProductIds().size() <= 50,
                "Chọn từ 1 đến 50 sản phẩm trên trang hiện tại.");
        require(form.getStatus() == ProductStatus.ACTIVE || form.getStatus() == ProductStatus.INACTIVE
                        || form.getStatus() == ProductStatus.DELETED,
                "Chọn Tạm ẩn, Mở bán hoặc Ngừng kinh doanh.");
        reason(form.getReason());
        require(form.getProductIds().stream().allMatch(id -> id != null && id > 0), "Danh sách sản phẩm không hợp lệ.");
        List<Long> ids = form.getProductIds().stream().distinct().sorted().toList();
        require(ids.size() == form.getProductIds().size(),
                "Danh sách sản phẩm không hợp lệ.");
        // Lock in ID order and check the entire selection before writing. The outer transaction
        // also rolls back every status/audit entry if an existing activation rule rejects one item.
        for (Long id : ids) {
            Product product = getOwnedProduct(authentication, id, true);
            requireWritableProduct(product);
            require(form.getStatus() == ProductStatus.DELETED
                            || product.getStatus() == (form.getStatus() == ProductStatus.ACTIVE ? ProductStatus.INACTIVE : ProductStatus.ACTIVE),
                    "Trạng thái sản phẩm đã thay đổi. Tải lại danh sách và chọn các sản phẩm cùng điều kiện.");
            if (form.getStatus() == ProductStatus.DELETED) {
                for (ProductVariant variant : productVariantRepository.findByProductIdOrderByVariantId(id)) {
                    require(!hasReservedStock(variant.getVariantId()),
                            "Không thể ngừng kinh doanh sản phẩm còn số lượng chờ xuất kho. Chưa có sản phẩm nào được thay đổi.");
                }
            }
        }
        for (Long id : ids) changeStatus(authentication, id, form);
    }

    @Override
    @Transactional
    public void changeStatus(Authentication authentication, Long productId, ActionRequest form) {
        Product product = getOwnedProduct(authentication, productId, true);
        requireWritableProduct(product);
        String changeReason = reason(form.getReason());
        require(form.getStatus() != null, "Chọn trạng thái sản phẩm hợp lệ.");
        List<ProductVariant> variants =
                productVariantRepository.findByProductIdOrderByVariantId(productId);
        if (form.getStatus() == ProductStatus.ACTIVE) {
            require(
                    !product.isModerationBlocked(),
                    "Sản phẩm đang bị Admin khóa. Chủ shop không thể tự mở khóa.");
            require(
                    categoryValidator.hasActiveCategory(productId),
                    "Sản phẩm chưa có danh mục còn hoạt động.");
            require(hasOpenVariant(variants, null), "Sản phẩm cần có ít nhất một phân loại đang bật để mở bán.");
        }
        if (form.getStatus() == ProductStatus.DELETED) {
            for (ProductVariant variant : variants) {
                require(
                        !hasReservedStock(variant.getVariantId()),
                        "Không thể ngừng kinh doanh sản phẩm còn số lượng chờ xuất kho.");
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
                    productStatusLabel(previousStatus) + " → " + productStatusLabel(form.getStatus()) + ": " + changeReason);
        }
    }

    private boolean hasReservedStock(Long variantId) {
        for (InventoryBatch batch :
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId)) {
            if (batch.getReservedQuantity() > 0) {
                return true;
            }
        }
        return false;
    }

    private void archiveVariants(List<ProductVariant> variants) {
        for (ProductVariant variant : variants) {
            variant.setStatus(VariantStatus.DELETED);
            variant.setUpdatedAt(now());
            productVariantRepository.save(variant);
        }
    }

    @Override
    @Transactional
    public void addVariant(Authentication authentication, Long productId, VariantRequest form) {
        Product product = getOwnedProduct(authentication, productId, true);
        requireWritableProduct(product);
        form.setSku(null);
        validateVariantFields(form, null);
        initialQuantity(form);
        String changeReason = reason(form.getReason());
        Long actorId = authService.getCurrentUserId(authentication);
        requestReapproval(product);
        createVariant(product, form, actorId);
        inventoryJournalService.audit(
                actorId,
                "ADD_SKU",
                "PRODUCT",
                productId,
                changeReason + "; thêm phân loại và yêu cầu hậu kiểm thông tin mới.");
    }

    @Override
    @Transactional
    public void updateVariant(
            Authentication authentication, Long productId, Long variantId, VariantRequest form) {
        Product product = getOwnedProduct(authentication, productId, true);
        requireWritableProduct(product);
        ProductVariant variant = getVariantForUpdate(productId, variantId);
        require(variant.getStatus() != VariantStatus.DELETED, "Phân loại đã ngừng kinh doanh.");
        // Ignore an altered or empty SKU submitted by the client.
        form.setSku(variant.getSku());
        validateVariantFields(form, variant);
        String changeReason = reason(form.getReason());
        boolean structureChanged = variantStructureChanged(variant, form);
        boolean otherFieldsChanged = structureChanged
                || !Objects.equals(variant.getThumbnailUrl(), form.getThumbnailUrl().trim())
                || !Objects.equals(variant.getLowStockThreshold(), form.getLowStockThreshold());
        BigDecimal previousPrice = variant.getPrice();
        BigDecimal previousCompareAtPrice = variant.getCompareAtPrice();
        boolean priceChanged = !sameDecimal(previousPrice, form.getPrice());
        boolean compareAtPriceChanged = !sameDecimal(previousCompareAtPrice, form.getCompareAtPrice());
        if (structureChanged) {
            requestReapproval(product);
        }
        applyVariantFields(variant, form);
        productVariantRepository.save(variant);
        savePrimaryImage(productId, variantId, form.getThumbnailUrl());
        Long actorId = authService.getCurrentUserId(authentication);
        if (priceChanged) {
            recordPriceChange(variant, actorId);
        }
        if (priceChanged || compareAtPriceChanged || otherFieldsChanged) {
            String auditReason = changeReason;
            if (priceChanged) {
                auditReason += "; giá bán " + auditPrice(previousPrice)
                        + " → " + auditPrice(variant.getPrice());
            }
            if (compareAtPriceChanged) {
                auditReason += "; giá trước giảm " + auditPrice(previousCompareAtPrice)
                        + " → " + auditPrice(variant.getCompareAtPrice());
            }
            if (structureChanged) {
                auditReason += "; thay đổi tên, khối lượng hoặc đơn vị bán; yêu cầu hậu kiểm thông tin mới.";
            }
            inventoryJournalService.audit(
                    actorId, otherFieldsChanged ? "UPDATE" : "PRICE", "VARIANT", variantId, auditReason);
        }
    }

    private boolean variantStructureChanged(ProductVariant variant, VariantRequest form) {
        return !Objects.equals(variant.getVariantName(), form.getVariantName().trim())
                || !Objects.equals(variant.getUnit(), form.getUnit())
                || !sameDecimal(variant.getWeight(), form.getWeight());
    }

    private boolean sameDecimal(BigDecimal current, BigDecimal requested) {
        if (current == null) {
            return requested == null;
        }
        return requested != null && current.compareTo(requested) == 0;
    }

    private String auditPrice(BigDecimal value) {
        return value == null ? "chưa thiết lập" : value.stripTrailingZeros().toPlainString() + " đ";
    }

    private void resetApproval(Product product) {
        product.setApprovalStatus(ApprovalStatus.PENDING);
        product.setApprovedBy(null);
        product.setApprovedAt(null);
    }

    private void requestReapproval(Product product) {
        if (product.getApprovalStatus() == ApprovalStatus.PENDING) {
            return;
        }
        resetApproval(product);
        product.setUpdatedAt(now());
        // A new review request does not change the seller's business status or admin lock.
        productRepository.saveAndFlush(product);
    }

    @Override
    @Transactional
    public void changeVariantStatus(
            Authentication authentication,
            Long productId,
            Long variantId,
            VariantActionRequest form) {
        Product product = getOwnedProduct(authentication, productId, true);
        requireWritableProduct(product);
        ProductVariant variant = getVariantForUpdate(productId, variantId);
        require(variant.getStatus() != VariantStatus.DELETED, "Phân loại đã ngừng kinh doanh.");
        VariantStatus requestedStatus = form.getStatus();
        require(
                requestedStatus == VariantStatus.ACTIVE
                        || requestedStatus == VariantStatus.INACTIVE
                        || requestedStatus == VariantStatus.DELETED,
                "Chọn trạng thái phân loại hợp lệ.");
        String changeReason = reason(form.getReason());
        if (requestedStatus == VariantStatus.DELETED) {
            require(!hasReservedStock(variantId), "Không thể ngừng kinh doanh phân loại còn số lượng chờ xuất kho.");
        }
        if (requestedStatus != VariantStatus.ACTIVE
                && product.getStatus() == ProductStatus.ACTIVE) {
            List<ProductVariant> variants =
                    productVariantRepository.findByProductIdOrderByVariantId(productId);
            require(
                    hasOpenVariant(variants, variantId),
                    "Tạm ẩn sản phẩm trước khi tạm dừng hoặc ngừng kinh doanh phân loại cuối cùng.");
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
                    variantStatusLabel(previousStatus) + " → " + variantStatusLabel(requestedStatus) + ": " + changeReason);
        }
    }

    private String productStatusLabel(ProductStatus status) {
        return switch (status) {
            case ACTIVE -> "Mở bán";
            case INACTIVE -> "Tạm ẩn";
            case DELETED -> "Ngừng kinh doanh";
        };
    }

    private String variantStatusLabel(VariantStatus status) {
        return switch (status) {
            case ACTIVE -> "Bật phân loại";
            case INACTIVE -> "Tạm dừng phân loại";
            case OUT_OF_STOCK -> "Hết hàng";
            case DELETED -> "Ngừng kinh doanh phân loại";
        };
    }

    @Override
    public ProductRequest editForm(Authentication authentication, Long productId) {
        ProductResponse response = get(authentication, productId);
        Product product = response.getProduct();
        ProductRequest form = new ProductRequest();
        form.setName(product.getName());
        form.setDescription(product.getDescription());
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

    @Override
    public Page<PriceHistory> priceHistory(
            Authentication authentication, Long productId, Long variantId, int page) {
        getOwnedProduct(authentication, productId, false);
        ProductVariant variant =
                productVariantRepository
                        .findById(variantId)
                        .orElseThrow(
                                () -> new AccessDeniedException("Phân loại không thuộc sản phẩm này."));
        if (!variant.getProductId().equals(productId)) {
            throw new AccessDeniedException("Phân loại không thuộc sản phẩm này.");
        }
        int pageIndex = Math.min(Math.max(1, page) - 1, Integer.MAX_VALUE / PageSizes.DEFAULT);
        PageRequest pageable = PageRequest.of(pageIndex, PageSizes.DEFAULT);
        Page<PriceHistory> result =
                priceHistoryRepository.findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(
                        variantId, pageable);
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            pageable = pageable.withPage(Math.max(0, result.getTotalPages() - 1));
            result =
                    priceHistoryRepository
                            .findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(
                                    variantId, pageable);
        }
        return result;
    }

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
