package VietFreshHub.order.repository;
import VietFreshHub.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
public interface OrderRepository extends JpaRepository<Order,Long> {
    interface StatusCount {
        Order.Status getStatus();
        Long getOrderCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @org.springframework.data.jpa.repository.Query("""
            select o.status as status, count(o) as orderCount
            from Order o where o.placedAt >= :from and o.placedAt < :to
            group by o.status order by o.status
            """)
    List<StatusCount> countOrdersByStatus(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                          @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    @Query("""
            select count(o)
            from Order o
            where o.status = VietFreshHub.order.entity.Order.Status.PENDING
              and o.placedAt >= :from and o.placedAt < :to
              and exists (
                  select s.shopOrderId from ShopOrder s
                  where s.order = o and s.status not in ('PENDING', 'CANCELLED')
              )
            """)
    long countPendingOrdersWithStartedShopOrder(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("select " + CustomerOrderStageQuery.EXPRESSION
            + " from Order o where o.orderId=:orderId and o.customer.userId=:customerId")
    Optional<String> findCustomerStage(
            @org.springframework.data.repository.query.Param("orderId") Long orderId,
            @org.springframework.data.repository.query.Param("customerId") Long customerId);

    String CUSTOMER_SHOP_STATUS = """
            case
                when s.status in ('CANCELLED', 'COMPLETED') then s.status
                when d.status in ('ACCEPTED', 'PICKED_UP') then 'READY_FOR_DELIVERY'
                when d.status in ('OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'REDELIVERY_SCHEDULED') then d.status
                else s.status
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
