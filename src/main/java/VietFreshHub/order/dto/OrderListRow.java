package VietFreshHub.order.dto;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderListRow(Long shopOrderId,Long orderId,String shopName,String status,
                           String paymentStatus,BigDecimal totalAmount,LocalDateTime placedAt) {
    public String orderCode() { return BusinessCodes.format("VFH",shopOrderId); }
    public String paymentStatusLabel() { return OrderLabels.payment(paymentStatus); }
    public String statusLabel() { return OrderLabels.customerStatus(status); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
