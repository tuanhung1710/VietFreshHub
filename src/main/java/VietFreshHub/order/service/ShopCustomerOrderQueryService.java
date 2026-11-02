package VietFreshHub.order.service;
import VietFreshHub.order.dto.OrderDetailsResponse;
public interface ShopCustomerOrderQueryService {
    Long getCartBadgeTotal(Long customerId);
    OrderDetailsResponse getDetails(Long customerId,Long orderId);
    OrderDetailsResponse getShopDetails(Long customerId,Long shopOrderId);
}
