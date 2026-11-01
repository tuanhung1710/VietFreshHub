package VietFreshHub.product.service;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.nearExpiry;
import static VietFreshHub.inventory.rule.InventoryRules.today;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.BatchMarkdownLookup;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductCategory;
import VietFreshHub.product.entity.ProductImage;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.ProductCategoryRepository;
import VietFreshHub.product.repository.ProductImageRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tải quan hệ và dựng dữ liệu danh mục; quyền truy cập được kiểm tra tại service gọi. */
@Component
@RequiredArgsConstructor
public class ProductCatalogReader {
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final BatchMarkdownLookup batchMarkdownLookup;
    private final ProductAvailabilityService productAvailabilityService;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final StockAllocationRepository stockAllocationRepository;

    /** Dựng trang chi tiết sau khi sản phẩm đã được kiểm tra quyền sở hữu. */
    public ProductResponse forProduct(Product product) {
        List<ProductVariant> variants = productVariantRepository.findByProductIdOrderByVariantId(product.getProductId());
        ProductManagementState state = productAvailabilityService.states(
                product.getShopId(), List.of(product.getProductId())).get(product.getProductId());
        return buildProductResponse(product, variants, findBatches(variants), true, state);
    }

    /**
     * Tải chung phân loại, lô và trạng thái của cả trang rồi ghép thành dữ liệu hiển thị sản phẩm.
     */
    public Page<ProductResponse> toPage(Page<Product> products) {
        // Gom ID của cả trang để tải chung phân loại, lô và trạng thái thay vì tải từng dòng.
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
        return products.map(product -> buildProductResponse(
                product, variantsForProduct(product.getProductId(), pageVariants), batches,
                false, states.get(product.getProductId())));
    }

    private List<ProductVariant> variantsForProduct(Long productId, List<ProductVariant> pageVariants) {
        List<ProductVariant> result = new ArrayList<>();
        for (ProductVariant variant : pageVariants) {
            if (variant.getProductId().equals(productId)) {
                result.add(variant);
            }
        }
        return result;
    }

    /** Tải lô của các phân loại theo hạn sử dụng và ID; bỏ qua truy vấn nếu danh sách rỗng. */
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

    /** Ghép sản phẩm với ảnh, danh mục, phân loại và trạng thái quản lý để hiển thị. */
    private ProductResponse buildProductResponse(
            Product product,
            List<ProductVariant> variants,
            List<InventoryBatch> batches,
            boolean details,
            ProductManagementState state) {
        // Chỉ hiển thị giá giảm khi cả sản phẩm và phân loại đang đủ điều kiện bán.
        LocalDate date = today();
        List<BatchMarkdown> markdowns = batchMarkdownLookup.findActive(batches);
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
        if (state == null) {
            state = new ProductManagementState("archived", "Sản phẩm đã xóa; tồn kho và lịch sử được giữ lại.");
        }
        response.setManagementState(state);
        response.setPublished(List.of("active", "out-of-stock").contains(state.code()));
        // Sau khi có đủ phân loại mới tổng hợp số phân loại, khoảng giá và thông tin kho.
        populateCatalogSummary(response);
        return response;
    }

    /**
     * Tổng hợp tồn thực tế, hàng giữ cho đơn, hàng khả dụng và cảnh báo từ các lô của phân loại.
     * Chỉ hiển thị giá giảm khi phân loại đủ điều kiện bán.
     */
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

        // Danh sách lô được tải chung cho nhiều SKU, nên chỉ cộng lô thuộc phân loại hiện tại.
        for (InventoryBatch batch : batches) {
            if (!batch.getVariantId().equals(variant.getVariantId())) {
                continue;
            }
            BatchResponse row =
                    buildBatchResponse(product, variant, batch, markdowns, details, date);
            batchRows.add(row);
            // Tồn thực tế gồm cả hàng bị khóa hoặc hết hạn; khả dụng đã loại các phần không thể bán.
            physicalQuantity += batch.getQuantityOnHand();
            reservedQuantity += batch.getReservedQuantity();
            availableQuantity += row.getAvailable();
            // Lô đã hết hàng không làm sản phẩm mang cảnh báo hạn sử dụng.
            if (batch.getQuantityOnHand() > 0) {
                hasExpiredStock = hasExpiredStock || row.isExpired();
                hasNearExpiryStock = hasNearExpiryStock || row.isNearExpiry();
            }
            // Chọn mức giảm thấp nhất trong các lô đang áp dụng hợp lệ.
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
        // Phần còn lại là hàng chưa giữ cho đơn nhưng không thể bán do tình trạng lô.
        response.setUnavailable(physicalQuantity - reservedQuantity - availableQuantity);
        response.setBatches(batchRows);
        response.setOfferPrice(offerPrice);
        // Lượng hàng giảm giá chỉ cộng từ các lô có đúng mức giá giảm đang hiển thị.
        response.setOfferQuantity(offerQuantity(batchRows, offerPrice));
        response.setLowStock(availableQuantity > 0 && availableQuantity <= lowStockThreshold);
        response.setBatchCount(batchRows.size());
        response.setExpired(hasExpiredStock);
        response.setNearExpiry(hasNearExpiryStock);
        return response;
    }

    /**
     * Tính tồn khả dụng, cảnh báo hạn và giá giảm hợp lệ cho lô; chỉ tải phân bổ khi xem chi tiết.
     */
    private BatchResponse buildBatchResponse(
            Product product,
            ProductVariant variant,
            InventoryBatch batch,
            List<BatchMarkdown> markdowns,
            boolean details,
            LocalDate date) {
        // Danh sách giảm giá đã sắp mới nhất trước; lấy bản ghi đầu tiên hợp lệ cho lô này.
        BatchMarkdown currentMarkdown = null;
        for (BatchMarkdown markdown : markdowns) {
            if (InventoryRules.isApplicableMarkdown(batch, markdown, variant.getPrice(), date)) {
                currentMarkdown = markdown;
                break;
            }
        }
        List<StockAllocation> allocations = List.of();
        // Chỉ trang chi tiết cần danh sách phân bổ; trang danh sách tránh tải thêm cho từng lô.
        if (details) {
            allocations =
                    stockAllocationRepository.findByBatchIdOrderByAllocationIdDesc(
                            batch.getBatchId());
        }
        return BatchResponse.builder()
                .batch(batch)
                .available(available(batch, date))
                .expired(expired(batch, date))
                .nearExpiry(nearExpiry(batch, date, product.getExpiryWarningDays()))
                .markdown(currentMarkdown)
                .allocations(allocations)
                .productName(product.getName())
                .sku(variant.getSku())
                .unit(variant.getUnit())
                .markdownEligible(InventoryRules.markdownEligible(batch, date))
                .expiryWarningDays(product.getExpiryWarningDays())
                .daysUntilExpiry(InventoryRules.daysUntilExpiry(batch, date))
                .build();
    }

    /** Cộng lượng khả dụng của những lô áp dụng đúng mức giá giảm được hiển thị. */
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

    /**
     * Tổng hợp số phân loại, khoảng giá, số lô và cảnh báo hạn sử dụng cho một sản phẩm.
     * Phân loại ngừng kinh doanh vẫn xuất hiện trong kho nếu còn tồn hoặc hàng giữ cho đơn.
     */
    private void populateCatalogSummary(ProductResponse response) {
        // Các phân loại chưa ngừng kinh doanh, dùng để hiển thị trên trang sản phẩm.
        List<VariantResponse> visibleVariants = new ArrayList<>();

        // Các phân loại cần hiển thị trong kho, kể cả phân loại đã ngừng nhưng còn hàng.
        List<VariantResponse> inventoryVariants = new ArrayList<>();

        // Tổng số lô của các phân loại được đưa vào danh sách kho.
        int batchCount = 0;

        for (VariantResponse row : response.getVariants()) {
            ProductVariant variant = row.getVariant();

            // Chỉ dùng phân loại chưa ngừng kinh doanh để tính số phân loại và khoảng giá.
            if (variant.getStatus() != VariantStatus.DELETED) {
                visibleVariants.add(row);

                response.setMinPrice(lowerPrice(response.getMinPrice(), variant.getPrice()));
                response.setMaxPrice(higherPrice(response.getMaxPrice(), variant.getPrice()));

                response.setMinComparePrice(
                        lowerPrice(response.getMinComparePrice(), variant.getCompareAtPrice()));
                response.setMaxComparePrice(
                        higherPrice(response.getMaxComparePrice(), variant.getCompareAtPrice()));
            }

            // Hiển thị trong kho nếu chưa ngừng kinh doanh, hoặc vẫn còn tồn thực tế,
            // hoặc còn hàng đã giữ cho đơn để người quản lý tiếp tục theo dõi, xử lý.
            if (variant.getStatus() != VariantStatus.DELETED
                    || row.getPhysical() > 0
                    || row.getReserved() > 0) {
                inventoryVariants.add(row);

                batchCount += row.getBatchCount();

                response.setExpired(response.isExpired() || row.isExpired());
                response.setNearExpiry(response.isNearExpiry() || row.isNearExpiry());
            }
        }

        // Lưu hai danh sách để giao diện sản phẩm và kho sử dụng đúng danh sách tương ứng.
        response.setVisibleVariants(visibleVariants);
        response.setInventoryVariants(inventoryVariants);

        // Số hiển thị ở cột "Phân loại", ví dụ: "2 phân loại".
        response.setVariantCount(visibleVariants.size());

        // Tổng số lô thuộc các phân loại được hiển thị trong kho.
        response.setBatchCount(batchCount);
    }

    /** Chọn giá nhỏ hơn trong hai giá, bỏ qua giá trị null. */
    private BigDecimal lowerPrice(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.compareTo(current) < 0) {
            return candidate;
        }
        return current;
    }

    /** Chọn giá lớn hơn trong hai giá, bỏ qua giá trị null. */
    private BigDecimal higherPrice(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.compareTo(current) > 0) {
            return candidate;
        }
        return current;
    }

    /** Lấy tên danh mục của sản phẩm, kèm nhãn thay thế nếu danh mục không còn tồn tại. */
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

    /** Tra tên người kiểm duyệt; trả về null nếu chưa duyệt hoặc tài khoản không còn tồn tại. */
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

    private String productImageUrl(Long productId) {
        return primaryProductImageUrl(productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId));
    }

    /** Chọn ảnh cấp sản phẩm đầu tiên, giữ nguyên thứ tự được repository trả về. */
    public static String primaryProductImageUrl(List<ProductImage> images) {
        for (ProductImage image : images) {
            if (image.getVariantId() == null) {
                return image.getImageUrl();
            }
        }
        return null;
    }
}
