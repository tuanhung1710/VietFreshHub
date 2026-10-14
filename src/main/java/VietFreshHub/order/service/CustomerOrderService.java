package VietFreshHub.order.service;
import VietFreshHub.order.dto.*;
import org.springframework.data.domain.Page;
public interface CustomerOrderService {
    Page<OrderListRow> history(Long customerId,CustomerOrderFilterRequest filter);
    OrderActionsResponse actions(Long customerId,Long orderId);
    void cancel(Long customerId,Long orderId,String reason);
    ReorderResult reorder(Long customerId,Long orderId);
    OrderTrackingResponse tracking(Long customerId,Long orderId);
}
