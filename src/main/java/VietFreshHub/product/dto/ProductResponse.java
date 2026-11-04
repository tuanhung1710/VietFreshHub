package VietFreshHub.product.dto;

import VietFreshHub.product.entity.Product;

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
    private ProductManagementState managementState;
    private List<VariantResponse> visibleVariants;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    public int getVariantCount() {
        return visibleVariants == null ? 0 : visibleVariants.size();
    }

    /** Đếm phân loại chưa ngừng kinh doanh đang có lượng khả dụng để hiển thị. */
    public long getAvailableVariantCount() {
        // Đếm số SKU có hàng khả dụng để hiện "x phân loại còn hàng", không cộng số lượng tồn của SKU.
        return visibleVariants == null ? 0 : visibleVariants.stream().filter(v -> v.getAvailable() > 0).count();
    }

    /**
     * Lấy thời điểm cập nhật, hoặc thời điểm tạo nếu chưa sửa, rồi đổi từ UTC sang giờ Việt Nam.
     */
    public LocalDateTime getCatalogUpdatedAt() {
        var date = product.getUpdatedAt() == null ? product.getCreatedAt() : product.getUpdatedAt();
        return date == null ? null : date.plusHours(7);
    }
}
