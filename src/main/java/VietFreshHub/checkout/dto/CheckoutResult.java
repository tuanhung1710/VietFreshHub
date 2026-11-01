package VietFreshHub.checkout.dto;
import java.math.BigDecimal;
public record CheckoutResult(Long orderId,String orderCode,BigDecimal grandTotal,Long paymentId,String paymentMethodCode) {
    public CheckoutResult(Long orderId,String orderCode,BigDecimal grandTotal) { this(orderId,orderCode,grandTotal,null,"COD"); }
}
