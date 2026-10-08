package VietFreshHub.Order.repository;
import VietFreshHub.Order.entity.ShopOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ShopOrderRepository extends JpaRepository<ShopOrder,Long> {
    @EntityGraph(attributePaths={"shop","items.variant.product"})
    List<ShopOrder> findByOrderOrderIdOrderByShopOrderIdAsc(Long orderId);
}
