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

    @org.springframework.data.jpa.repository.Query("select new VietFreshHub.order.dto.OrderListRow(o.orderId,o.orderCode,o.status,o.paymentStatus,o.grandTotal,o.placedAt) "
            + "from Order o where o.customer.userId=:customerId "
            + "and (:status = '' or exists (select s.shopOrderId from ShopOrder s "
            + "left join OrderDeliveryView d on d.shopOrder = s where s.order = o and ("
            + CUSTOMER_SHOP_STATUS + ") = :status)) "
            + "and (:keyword='' or locate(lower(:keyword),lower(o.orderCode))>0) "
            + "and (:fromDate is null or o.placedAt>=:fromDate) and (:untilDate is null or o.placedAt<:untilDate)")
    org.springframework.data.domain.Page<VietFreshHub.order.dto.OrderListRow> findHistory(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,
            @org.springframework.data.repository.query.Param("untilDate") java.time.LocalDateTime untilDate,
            org.springframework.data.domain.Pageable pageable);

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
