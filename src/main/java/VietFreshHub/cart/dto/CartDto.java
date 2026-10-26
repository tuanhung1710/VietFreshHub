package VietFreshHub.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartDto {

    private Long cartId;
    private Long userId;
    private String status;
    @Builder.Default
    private List<CartItemDto> items = new ArrayList<>();

    // Bao gồm cả số lượng của những món hiện không thể mua.
    private long totalItems;
    // Chỉ cộng tiền những món hiện có thể mua.
    private BigDecimal totalAmount;
    private boolean hasUnavailableItems;
}
