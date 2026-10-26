package VietFreshHub.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {

    private Long cartItemId;
    private Long variantId;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal itemSubtotal;
    private Integer availableStock;
    private String status;
    private String imageUrl;
    private Long shopId;
    private String shopName;
    private String productName;

    // Có thể mua khi sản phẩm được bán và tồn kho đủ số lượng đã chọn.
    private boolean purchasable;
    // Vẫn cho sửa số lượng khi tồn kho còn hàng nhưng chưa đủ số lượng đã chọn.
    private boolean quantityEditable;
    private boolean priceChanged;
    // Giá đã lưu ở lần thêm hoặc cập nhật trước, dùng để so sánh với giá hiện tại.
    private BigDecimal previousPrice;
    private String availabilityMessage;
}
