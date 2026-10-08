package VietFreshHub.Order.service;
import VietFreshHub.Order.dto.OrderDetailsResponse;
public interface CustomerOrderQueryService {
    OrderDetailsResponse getDetails(Long customerId,Long orderId);
}
