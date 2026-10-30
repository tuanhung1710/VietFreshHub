package VietFreshHub.order.repository;
import VietFreshHub.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order,Long> {
    String CUSTOMER_SHOP_STATUS = """
            case
                when s.status = 'CANCELLED' then 'CANCELLED'
                when d.status in ('ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED') then d.status
                when d.deliveryId is null or d.status in ('UNASSIGNED', 'ASSIGNED') then s.status
                else null
            end
            """;

    @Query("select new VietFreshHub.order.dto.CustomerShopOrderStatus(s.order.orderId, s.shopOrderId, s.shop.shopName, "
            + CUSTOMER_SHOP_STATUS + ") from ShopOrder s left join OrderDeliveryView d on d.shopOrder = s "
            + "where s.order.customer.userId = :customerId and s.order.orderId in :orderIds order by s.shopOrderId")
    java.util.List<VietFreshHub.order.dto.CustomerShopOrderStatus> findCustomerShopStatuses(
            @Param("customerId") Long customerId, @Param("orderIds") java.util.List<Long> orderIds);
    Optional<Order> findByOrderCodeAndCustomerUserId(String code, Long customerId);
    Optional<Order> findByOrderIdAndCustomerUserId(Long orderId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId=:orderId")
    Optional<Order> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
