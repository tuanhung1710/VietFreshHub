package VietFreshHub.order.repository;
import VietFreshHub.order.entity.OrderDeliveryView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface OrderDeliveryReadRepository extends Repository<OrderDeliveryView,Long> {
    List<OrderDeliveryView> findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(Long orderId);
    List<OrderDeliveryView> findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List<Long> shopOrderIds);

    Optional<OrderDeliveryView> findByShopOrder_ShopOrderIdAndShopOrder_ShopId(Long shopOrderId, Long shopId);

    @Query("select d.shopOrder.shopOrderId from OrderDeliveryView d "
            + "where d.shopOrder.shopOrderId in :shopOrderIds and d.status = 'FAILED'")
    List<Long> findFailedShopOrderIds(@Param("shopOrderIds") List<Long> shopOrderIds);
}
