package VietFreshHub.product.dto;

import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.VariantStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VariantResponse {

    private ProductVariant variant;
    private String productName;
    private long physical;
    private long reserved;
    private long available;
    private long unavailable;
    private BigDecimal offerPrice;
    private long offerQuantity;
    private boolean lowStock;

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

    /** Chỉ trả giá trước giảm khi là số nguyên đồng không âm và không thấp hơn giá bán hiện tại. */
    public BigDecimal getReferencePrice() {
        BigDecimal reference = variant.getCompareAtPrice();
        BigDecimal price = variant.getPrice();
        return reference != null && price != null && reference.stripTrailingZeros().scale() <= 0
                && reference.signum() >= 0 && reference.compareTo(price) >= 0 ? reference : null;
    }
}
