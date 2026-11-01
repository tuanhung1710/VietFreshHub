package VietFreshHub.order.service;
import VietFreshHub.order.dto.OrderDetailsResponse;
public interface CustomerOrderQueryService {
    Long getCartBadgeTotal(Long customerId);
    OrderDetailsResponse getDetails(Long customerId,Long orderId);
}
