package VietFreshHub.order.service;

import VietFreshHub.order.dto.IncomingOrderResponse;
import VietFreshHub.order.dto.OrderCountsResponse;
import VietFreshHub.order.dto.OrderDetailResponse;
import VietFreshHub.order.dto.OrderFilterRequest;
import VietFreshHub.order.dto.OrderProcessingHistoryEntry;
import VietFreshHub.order.dto.ProcessedOrderResponse;
import VietFreshHub.order.entity.OrderAddress;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

    Page<IncomingOrderResponse> getIncomingOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getAllOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getConfirmedOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getPreparingOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getReadyOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getFailedOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<IncomingOrderResponse> getCancelledOrders(Authentication authentication, OrderFilterRequest filter, int page);

    Page<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter, int page);

    long countFilteredOrders(Authentication authentication, OrderFilterRequest filter, String view);

    OrderCountsResponse getOrderCounts(Authentication authentication);

    OrderDetailResponse getOrderDetail(Long shopOrderId, Authentication authentication);

    Long getDeliveryIdForManagedShopOrder(Long shopOrderId, Authentication authentication);

    OrderAddress getOrderAddress(Long orderId);

    List<OrderProcessingHistoryEntry> getProcessingHistoryForAdmin(Long shopOrderId, Authentication authentication);

    void lockOrderForDeliveryCompletion(Long orderId);

    void startShopOrderDelivery(Long shopOrderId, Long changedBy);

    void completeDeliveredShopOrder(Long shopOrderId, LocalDateTime completedAt, Long changedBy);

    void confirmOrder(Long shopOrderId, Authentication authentication);

    void startPreparing(Long shopOrderId, Authentication authentication);

    void markReady(Long shopOrderId, Authentication authentication);

}
