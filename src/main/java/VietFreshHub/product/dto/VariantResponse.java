package VietFreshHub.product.dto;

import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.VariantStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class VariantResponse {

    private ProductVariant variant;
    private String productName;
    private long physical;
    private long reserved;
    private long available;
    private long unavailable;
    private List<BatchResponse> batches;
    private List<BatchResponse> matchedBatches;
    private BigDecimal offerPrice;
    private long offerQuantity;
    private boolean lowStock;
    private int batchCount;
    private boolean expired;
    private boolean nearExpiry;

    /** OUT_OF_STOCK vẫn là cấu hình mở bán; tồn kho được hiển thị riêng. */
    public boolean isEnabled() {
        return variant != null && (variant.getStatus() == VariantStatus.ACTIVE
                || variant.getStatus() == VariantStatus.OUT_OF_STOCK);
    }

    public String getConfigurationLabel() {
        if (variant != null && variant.getStatus() == VariantStatus.DELETED) {
            return "Đã xóa";
        }
        return isEnabled() ? "Mở bán" : "Tạm ẩn";
    }

    /** Ưu tiên các lô khớp bộ lọc; nếu chưa lọc thì trả toàn bộ lô của phân loại. */
    public List<BatchResponse> getInventoryBatches() {
        return matchedBatches == null ? batches : matchedBatches;
    }

    /** Chỉ trả giá trước giảm khi là số nguyên đồng không âm và không thấp hơn giá bán hiện tại. */
    public BigDecimal getReferencePrice() {
        BigDecimal reference = variant.getCompareAtPrice();
        BigDecimal price = variant.getPrice();
        return reference != null && price != null && reference.stripTrailingZeros().scale() <= 0
                && reference.signum() >= 0 && reference.compareTo(price) >= 0 ? reference : null;
    }
}
