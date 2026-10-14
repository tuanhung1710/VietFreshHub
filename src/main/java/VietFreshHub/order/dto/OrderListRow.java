package VietFreshHub.order.dto;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderListRow(Long orderId,String orderCode,Order.Status status,String paymentStatus,
                           BigDecimal grandTotal,LocalDateTime placedAt) {
    public String paymentStatusLabel() { return OrderLabels.payment(paymentStatus); }
    public String statusLabel() { return OrderLabels.order(status); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
