package VietFreshHub.order.repository;
import VietFreshHub.order.entity.OrderDeliveryView;
import org.springframework.data.repository.Repository;
import java.util.List;
public interface OrderDeliveryReadRepository extends Repository<OrderDeliveryView,Long> {
    List<OrderDeliveryView> findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(Long orderId);
}
