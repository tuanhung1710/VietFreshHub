package VietFreshHub.order.dto;
import VietFreshHub.common.BusinessCodes;
import java.time.LocalDateTime;
import java.util.List;
public record OrderTrackingResponse(Long orderId,String orderCode,String statusLabel,String status,List<ShopTracking> shops) {
    public record ShopTracking(Long shopOrderId,String shopName,String orderStatus,String orderStatusCode,String deliveryStatus,String deliveryStatusCode,List<Event> events) {
        public String orderCode() { return BusinessCodes.format("VFH",shopOrderId); }
    }
    public record Event(LocalDateTime occurredAt,String label,String note,String kind) {}
}
