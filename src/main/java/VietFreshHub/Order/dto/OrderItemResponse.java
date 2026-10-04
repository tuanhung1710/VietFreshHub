package VietFreshHub.Order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderItemResponse {

    private String productName;
    private String variantName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
}
