package VietFreshHub.product.dto;

import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ProductStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ProductResponse {

    private Product product;
    private String imageUrl;
    private String categoryName;
    private List<VariantResponse> variants;
    private List<String> categoryNames;
    private String approvedByName;
    private boolean published;
    private ProductManagementState managementState;
    private List<VariantResponse> visibleVariants;
    private List<VariantResponse> inventoryVariants;
    private int inventoryVariantTotal;
    private int variantCount;
    private int batchCount;
    private boolean expired;
    private boolean nearExpiry;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minComparePrice;
    private BigDecimal maxComparePrice;

    /**
     * Đếm lô hết hạn còn hàng có thể hủy trên toàn bộ sản phẩm, kể cả khi giao diện đang lọc một SKU hoặc lô.
     */
    public long getExpiredDisposableBatchCount() {
        if (variants == null) {
            return 0;
        }
        return variants.stream().filter(v -> v.getBatches() != null)
                .flatMap(v -> v.getBatches().stream()).filter(BatchResponse::isCanDispose)
                .count();
    }

    /**
     * Cho phép ngừng kinh doanh khi sản phẩm chưa ngừng và không phân loại nào còn hàng giữ cho đơn.
     */
    public boolean isCanDelete() {
        return product.getStatus() != ProductStatus.DELETED
                && variants != null && variants.stream().noneMatch(v -> v.getReserved() > 0);
    }

    /** Đếm phân loại chưa ngừng kinh doanh đang có lượng khả dụng để hiển thị. */
    public long getAvailableVariantCount() {
        // Đếm số SKU có hàng khả dụng để hiện "x phân loại còn hàng", không cộng số lượng tồn của SKU.
        return visibleVariants == null ? 0 : visibleVariants.stream().filter(v -> v.getAvailable() > 0).count();
    }

    /** Cộng tồn thực tế của các phân loại đang được hiển thị trong kho. */
    public long getInventoryPhysical() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getPhysical).sum();
    }

    /** Cộng lượng đã giữ cho đơn của các phân loại đang được hiển thị trong kho. */
    public long getInventoryReserved() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getReserved).sum();
    }

    /** Cộng lượng khả dụng của các phân loại đang được hiển thị trong kho. */
    public long getInventoryAvailable() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getAvailable).sum();
    }

    /**
     * Lấy thời điểm cập nhật, hoặc thời điểm tạo nếu chưa sửa, rồi đổi từ UTC sang giờ Việt Nam.
     */
    public LocalDateTime getCatalogUpdatedAt() {
        var date = product.getUpdatedAt() == null ? product.getCreatedAt() : product.getUpdatedAt();
        return date == null ? null : date.plusHours(7);
    }
}
