package VietFreshHub.order.dto;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderListRow(Long orderId,String orderCode,Order.Status status,String paymentStatus,
                           BigDecimal grandTotal,LocalDateTime placedAt,String stage) {
    public OrderListRow(Long orderId,String orderCode,Order.Status status,String paymentStatus,BigDecimal grandTotal,LocalDateTime placedAt) {
        this(orderId,orderCode,status,paymentStatus,grandTotal,placedAt,
                status==Order.Status.CANCELLED ? "CANCELLED" : status==Order.Status.COMPLETED ? "COMPLETED"
                : status==Order.Status.PENDING ? "WAITING_CONFIRMATION" : "PREPARING");
    }
    public String paymentStatusLabel() { return OrderLabels.payment(paymentStatus); }
    public String statusLabel() { return CustomerOrderStage.valueOf(stage).label(); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
