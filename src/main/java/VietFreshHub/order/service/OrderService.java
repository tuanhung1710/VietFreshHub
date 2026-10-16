package VietFreshHub.order.service;

import VietFreshHub.order.dto.IncomingOrderResponse;
import VietFreshHub.order.dto.OrderCountsResponse;
import VietFreshHub.order.dto.OrderDetailResponse;
import VietFreshHub.order.dto.OrderFilterRequest;
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

    Page<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter, int page);

    long countFilteredOrders(Authentication authentication, OrderFilterRequest filter, String view);

    OrderCountsResponse getOrderCounts(Authentication authentication);

    OrderDetailResponse getOrderDetail(Long shopOrderId, Authentication authentication);

    OrderAddress getOrderAddress(Long orderId);

    void lockOrderForDeliveryCompletion(Long orderId);

    void completeDeliveredShopOrder(Long shopOrderId, LocalDateTime completedAt);

    void confirmOrder(Long shopOrderId, Authentication authentication);

    void startPreparing(Long shopOrderId, Authentication authentication);

    void markReady(Long shopOrderId, Authentication authentication);

    void confirmOrders(List<Long> shopOrderIds, Authentication authentication);

    void startPreparingOrders(List<Long> shopOrderIds, Authentication authentication);

    void markOrdersReady(List<Long> shopOrderIds, Authentication authentication);

    List<OrderDetailResponse> getBulkInvoices(List<Long> shopOrderIds, Authentication authentication, String view);
}
