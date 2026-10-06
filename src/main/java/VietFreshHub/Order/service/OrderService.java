package VietFreshHub.Order.service;

import VietFreshHub.Order.dto.IncomingOrderResponse;
import VietFreshHub.Order.dto.OrderCountsResponse;
import VietFreshHub.Order.dto.OrderDetailResponse;
import VietFreshHub.Order.dto.OrderFilterRequest;
import VietFreshHub.Order.dto.ProcessedOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

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

    void confirmOrder(Long shopOrderId, Authentication authentication);

    void startPreparing(Long shopOrderId, Authentication authentication);

    void markReady(Long shopOrderId, Authentication authentication);

    void confirmOrders(List<Long> shopOrderIds, Authentication authentication);

    void startPreparingOrders(List<Long> shopOrderIds, Authentication authentication);

    void markOrdersReady(List<Long> shopOrderIds, Authentication authentication);

    List<OrderDetailResponse> getBulkInvoices(List<Long> shopOrderIds, Authentication authentication, String view);
}
