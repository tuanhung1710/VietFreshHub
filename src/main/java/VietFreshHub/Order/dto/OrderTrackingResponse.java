package VietFreshHub.Order.dto;
import java.time.LocalDateTime;
import java.util.List;
public record OrderTrackingResponse(Long orderId,String orderCode,String statusLabel,List<ShopTracking> shops) {
    public record ShopTracking(Long shopOrderId,String shopName,String orderStatus,String deliveryStatus,String failureReason,List<Event> events) {}
    public record Event(LocalDateTime occurredAt,String label,String note,String kind) {}
}
