package VietFreshHub.Order.service;
import VietFreshHub.Order.dto.OrderDetailsResponse;
public interface CustomerOrderQueryService {
    Long getCartBadgeTotal(Long customerId);
    OrderDetailsResponse getDetails(Long customerId,Long orderId);
}
