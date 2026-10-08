package VietFreshHub.Order.repository;
import VietFreshHub.Order.entity.OrderDeliveryView;
import org.springframework.data.repository.Repository;
import java.util.List;
public interface OrderDeliveryReadRepository extends Repository<OrderDeliveryView,Long> {
    List<OrderDeliveryView> findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(Long orderId);
}
