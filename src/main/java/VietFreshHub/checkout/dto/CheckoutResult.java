package VietFreshHub.checkout.dto;
import java.math.BigDecimal;
public record CheckoutResult(Long orderId,String orderCode,BigDecimal grandTotal) {}
