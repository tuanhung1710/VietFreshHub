package VietFreshHub.Order.dto;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.util.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderListRow(Long orderId,String orderCode,Order.Status status,Order.PaymentState paymentStatus,
                           BigDecimal grandTotal,LocalDateTime placedAt) {
    public String statusLabel() { return OrderLabels.order(status); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
