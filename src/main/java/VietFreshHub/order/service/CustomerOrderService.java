package VietFreshHub.order.service;
import VietFreshHub.order.dto.*;
import org.springframework.data.domain.Page;
public interface CustomerOrderService {
    Page<OrderListRow> history(Long customerId,CustomerOrderFilterRequest filter);
    OrderActionsResponse shopActions(Long customerId,Long shopOrderId);
    void cancelShop(Long customerId,Long shopOrderId,String reason);
    ReorderResult reorderShop(Long customerId,Long shopOrderId);
    OrderTrackingResponse shopTracking(Long customerId,Long shopOrderId);
}
