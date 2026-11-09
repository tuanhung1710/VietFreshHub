package VietFreshHub.product.service;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
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
    private final ProductAvailabilityService productAvailabilityService;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;

    /** Dựng trang chi tiết sau khi sản phẩm đã được kiểm tra quyền sở hữu. */
    public ProductResponse forProduct(Product product) {
        List<ProductVariant> variants =
                productVariantRepository.findByProductIdOrderByVariantId(product.getProductId());
        ProductManagementState state =
                productAvailabilityService
                        .states(product.getShopId(), List.of(product.getProductId()))
                        .get(product.getProductId());
        return buildProductResponse(product, variants, findBatches(variants), state);
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

        // Java có quy tắc: biến cục bộ đã bị gán lại thì không được dùng trực tiếp trong lambda.
        List<ProductVariant> pageVariants = variants;

        // lay cac lo tuong ung cua cac variant
        List<InventoryBatch> batches = findBatches(variants);

        // lấy trạng thái quản lý của từng sản phẩm
        Map<Long, ProductManagementState> states =
                products.isEmpty()
                        ? Map.of()
                        : productAvailabilityService.states(
                                products.getContent().getFirst().getShopId(), productIds);

        // ghép dữ liệu thành ProductResponse cho từng sản pham
        return products.map(
                product ->
                        buildProductResponse(
                                product,
                                variantsForProduct(product.getProductId(), pageVariants),
                                batches,
                                states.get(product.getProductId())));
    }

    private List<ProductVariant> variantsForProduct(
            Long productId, List<ProductVariant> pageVariants) {
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
            ProductManagementState state) {
        LocalDate date = today();
        List<VariantResponse> variantRows = new ArrayList<>();
        for (ProductVariant variant : variants) {
            variantRows.add(buildVariantResponse(product, variant, batches, date));
        }

        ProductResponse response = new ProductResponse();
        response.setProduct(product);
        response.setImageUrl(productImageUrl(product.getProductId()));
        response.setCategoryName(String.join(", ", categoryNames(product.getProductId())));
        response.setVariants(variantRows);
        if (state == null) {
            state =
                    new ProductManagementState(
                            "archived", "Sản phẩm đã xóa; tồn kho và lịch sử được giữ lại.");
        }
        response.setManagementState(state);
        populateCatalogSummary(response);
        return response;
    }

    /**
     * Tổng hợp tồn thực tế, hàng giữ cho đơn và hàng khả dụng từ các lô của phân loại.
     */
    private VariantResponse buildVariantResponse(
            Product product,
            ProductVariant variant,
            List<InventoryBatch> batches,
            LocalDate date) {
        long physicalQuantity = 0;
        long reservedQuantity = 0;
        long availableQuantity = 0;

        // Danh sách lô được tải chung cho nhiều SKU, nên chỉ cộng lô thuộc phân loại hiện tại.
        for (InventoryBatch batch : batches) {
            if (!batch.getVariantId().equals(variant.getVariantId())) {
                continue;
            }
            int batchAvailable = available(batch, date);
            // Tồn thực tế gồm cả hàng bị khóa hoặc hết hạn; khả dụng đã loại các phần không thể
            // bán.
            physicalQuantity += batch.getQuantityOnHand();
            reservedQuantity += batch.getReservedQuantity();
            availableQuantity += batchAvailable;
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
        response.setLowStock(availableQuantity > 0 && availableQuantity <= lowStockThreshold);
        return response;
    }

    /** Tổng hợp số phân loại còn kinh doanh và khoảng giá của sản phẩm. */
    private void populateCatalogSummary(ProductResponse response) {
        // Các phân loại chưa ngừng kinh doanh, dùng để hiển thị trên trang sản phẩm.
        List<VariantResponse> visibleVariants = new ArrayList<>();

        for (VariantResponse row : response.getVariants()) {
            ProductVariant variant = row.getVariant();

            // Chỉ dùng phân loại chưa ngừng kinh doanh để tính số phân loại và khoảng giá.
            if (variant.getStatus() != VariantStatus.DELETED) {
                visibleVariants.add(row);

                response.setMinPrice(lowerPrice(response.getMinPrice(), variant.getPrice()));
                response.setMaxPrice(higherPrice(response.getMaxPrice(), variant.getPrice()));
            }
        }

        response.setVisibleVariants(visibleVariants);
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

    private String productImageUrl(Long productId) {
        return primaryProductImageUrl(
                productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId));
    }

    /** Chọn ảnh cấp sản phẩm đầu tiên, giữ nguyên thứ tự được repository trả về. */
    public static String primaryProductImageUrl(List<ProductImage> images) {
        String fallback = null;
        for (ProductImage image : images) {
            if (image.getVariantId() == null
                    && image.getImageUrl() != null
                    && !image.getImageUrl().isBlank()) {
                if (Boolean.TRUE.equals(image.getPrimary())) {
                    return image.getImageUrl();
                }
                if (fallback == null) {
                    fallback = image.getImageUrl();
                }
            }
        }
        return fallback;
    }
}
