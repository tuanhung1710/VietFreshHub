package VietFreshHub.Order.service;

import VietFreshHub.Order.dto.IncomingOrderResponse;
import VietFreshHub.Order.dto.OrderCountsResponse;
import VietFreshHub.Order.dto.OrderDetailResponse;
import VietFreshHub.Order.dto.OrderFilterRequest;
import VietFreshHub.Order.dto.ProcessedOrderResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface OrderService {

    List<IncomingOrderResponse> getIncomingOrders(Authentication authentication, OrderFilterRequest filter);

    List<IncomingOrderResponse> getAllOrders(Authentication authentication, OrderFilterRequest filter);

    List<IncomingOrderResponse> getConfirmedOrders(Authentication authentication, OrderFilterRequest filter);

    List<IncomingOrderResponse> getPreparingOrders(Authentication authentication, OrderFilterRequest filter);

    List<IncomingOrderResponse> getReadyOrders(Authentication authentication, OrderFilterRequest filter);

    List<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter);

    long countFilteredOrders(Authentication authentication, OrderFilterRequest filter, String view);

    OrderCountsResponse getOrderCounts(Authentication authentication);

    OrderDetailResponse getOrderDetail(Long shopOrderId, Authentication authentication);

    void confirmOrder(Long shopOrderId, Authentication authentication);

    void startPreparing(Long shopOrderId, Authentication authentication);

    void markReady(Long shopOrderId, Authentication authentication);
}
