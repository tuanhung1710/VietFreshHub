package VietFreshHub.order.dto;
import java.time.LocalDateTime;
import java.util.List;
public record OrderTrackingResponse(Long orderId,String orderCode,String statusLabel,String status,List<ShopTracking> shops) {
    public record ShopTracking(Long shopOrderId,String shopName,String orderStatus,String orderStatusCode,String deliveryStatus,String deliveryStatusCode,String failureReason,List<Event> events) {
        public boolean hasVerifiedEvents() { return events.stream().anyMatch(Event::verified); }
    }
    /** verified means persisted history or an explicit stored timestamp, not a milestone inferred from current state. */
    public record Event(LocalDateTime occurredAt,String label,String note,String kind,boolean verified) {
        public Event(LocalDateTime occurredAt,String label,String note,String kind) {
            this(occurredAt,label,note,kind,true);
        }
    }
}
