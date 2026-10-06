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
public class AddToCartResponse {

    private Long cartItemId;
    private Long cartId;
    private Long variantId;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal itemSubtotal;
    private String message; // MSG21: "Added to cart."
}
