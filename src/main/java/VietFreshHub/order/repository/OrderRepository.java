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

    @org.springframework.data.jpa.repository.Query("select new VietFreshHub.order.dto.OrderListRow(o.orderId,o.orderCode,o.status,o.paymentStatus,o.grandTotal,o.placedAt, "
            + CustomerOrderStageQuery.EXPRESSION + ") from Order o where o.customer.userId=:customerId "
            + "and (:status='' or :status=" + CustomerOrderStageQuery.EXPRESSION + ") "
            + "and (:keyword='' or locate(lower(:keyword),lower(o.orderCode))>0 "
            + "or locate(lower(:keyword),lower(" + VietFreshHub.common.OrderCodes.DISPLAY_EXPRESSION + "))>0) "
            + "and (:fromDate is null or o.placedAt>=:fromDate) and (:untilDate is null or o.placedAt<:untilDate)")
    org.springframework.data.domain.Page<VietFreshHub.order.dto.OrderListRow> findHistory(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,
            @org.springframework.data.repository.query.Param("untilDate") java.time.LocalDateTime untilDate,
            org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select " + CustomerOrderStageQuery.EXPRESSION
            + " from Order o where o.orderId=:orderId and o.customer.userId=:customerId")
    Optional<String> findCustomerStage(
            @org.springframework.data.repository.query.Param("orderId") Long orderId,
            @org.springframework.data.repository.query.Param("customerId") Long customerId);

    @org.springframework.data.jpa.repository.Query("select o from Order o where o.customer.userId=:customerId and o.orderId in :orderIds")
    java.util.List<Order> findCustomerHistoryOrders(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("orderIds") java.util.List<Long> orderIds);

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
