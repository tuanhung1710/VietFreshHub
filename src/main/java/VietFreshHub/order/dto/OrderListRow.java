package VietFreshHub.order.dto;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderListRow(Long shopOrderId,Long orderId,String shopName,String status,
                           String paymentStatus,BigDecimal totalAmount,LocalDateTime placedAt,String paymentStatusLabel) {
    public OrderListRow(Long shopOrderId,Long orderId,String shopName,String status,
                        String paymentStatus,BigDecimal totalAmount,LocalDateTime placedAt) {
        this(shopOrderId,orderId,shopName,status,paymentStatus,totalAmount,placedAt,OrderLabels.payment(paymentStatus));
    }
    public String orderCode() { return BusinessCodes.format("VFH",shopOrderId); }
    public String statusLabel() { return OrderLabels.customerStatus(status); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
