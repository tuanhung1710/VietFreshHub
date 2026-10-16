package VietFreshHub.order.repository;
import VietFreshHub.order.entity.OrderDeliveryHistoryView;
import org.springframework.data.repository.Repository;
import java.util.List;
public interface OrderDeliveryHistoryReadRepository extends Repository<OrderDeliveryHistoryView,Long> {
    List<OrderDeliveryHistoryView> findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(List<Long> ids);
}
