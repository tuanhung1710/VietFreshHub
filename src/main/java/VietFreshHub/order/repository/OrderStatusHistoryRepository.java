package VietFreshHub.order.repository;
import VietFreshHub.order.entity.OrderStatusHistory;
import org.springframework.data.repository.Repository;
import java.util.List;
public interface OrderStatusHistoryRepository extends Repository<OrderStatusHistory,Long> {
    List<OrderStatusHistory> findByShopOrderShopOrderIdInOrderByCreatedAtAscHistoryIdAsc(List<Long> shopOrderIds);
    List<OrderStatusHistory> findByShopOrderShopOrderIdOrderByCreatedAtAscHistoryIdAsc(Long shopOrderId);
    List<OrderStatusHistory> findByShopOrder_ShopOrderIdAndStatusInOrderByCreatedAtAscHistoryIdAsc(
            Long shopOrderId, List<String> statuses);
}
