package VietFreshHub.Cart.dto;

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
}
