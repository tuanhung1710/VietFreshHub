package VietFreshHub.order.repository;
import VietFreshHub.order.entity.OrderDeliveryView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface OrderDeliveryReadRepository extends Repository<OrderDeliveryView,Long> {
    List<OrderDeliveryView> findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(Long orderId);

    @Query("select d from OrderDeliveryView d where d.shopOrder.order.customer.userId=:customerId "
            + "and d.shopOrder.order.orderId in :orderIds order by d.deliveryId")
    List<OrderDeliveryView> findCustomerHistoryDeliveries(@Param("customerId") Long customerId,
            @Param("orderIds") List<Long> orderIds);

    Optional<OrderDeliveryView> findByShopOrder_ShopOrderIdAndShopOrder_ShopId(Long shopOrderId, Long shopId);

    @Query("select d.shopOrder.shopOrderId from OrderDeliveryView d "
            + "where d.shopOrder.shopOrderId in :shopOrderIds and d.status = 'FAILED'")
    List<Long> findFailedShopOrderIds(@Param("shopOrderIds") List<Long> shopOrderIds);
}
