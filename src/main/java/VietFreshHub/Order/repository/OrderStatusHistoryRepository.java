package VietFreshHub.Order.repository;
import VietFreshHub.Order.entity.OrderStatusHistory;
import org.springframework.data.repository.Repository;
import java.util.List;
public interface OrderStatusHistoryRepository extends Repository<OrderStatusHistory,Long> {
    List<OrderStatusHistory> findByShopOrderOrderOrderIdOrderByCreatedAtAscHistoryIdAsc(Long orderId);
}
