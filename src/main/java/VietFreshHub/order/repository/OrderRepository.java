package VietFreshHub.order.repository;
import VietFreshHub.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order,Long> {
    @org.springframework.data.jpa.repository.Query("select new VietFreshHub.order.dto.OrderListRow(o.orderId,o.orderCode,o.status,o.paymentStatus,o.grandTotal,o.placedAt) "
            + "from Order o where o.customer.userId=:customerId and (:status is null or o.status=:status) "
            + "and (:keyword='' or locate(lower(:keyword),lower(o.orderCode))>0) "
            + "and (:fromDate is null or o.placedAt>=:fromDate) and (:untilDate is null or o.placedAt<:untilDate) "
            + "and (:delivering=false or (o.status not in (VietFreshHub.order.entity.Order.Status.CANCELLED,VietFreshHub.order.entity.Order.Status.COMPLETED) "
            + "and exists(select s.shopOrderId from ShopOrder s where s.order=o and s.status='OUT_FOR_DELIVERY' "
            + "and not exists(select d.deliveryId from OrderDeliveryView d where d.shopOrder=s and d.status not in ('PICKED_UP','OUT_FOR_DELIVERY')))))")
    org.springframework.data.domain.Page<VietFreshHub.order.dto.OrderListRow> findHistory(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") Order.Status status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,
            @org.springframework.data.repository.query.Param("untilDate") java.time.LocalDateTime untilDate,
            @org.springframework.data.repository.query.Param("delivering") boolean delivering,
            org.springframework.data.domain.Pageable pageable);
    Optional<Order> findByOrderCodeAndCustomerUserId(String code, Long customerId);
    Optional<Order> findByOrderIdAndCustomerUserId(Long orderId, Long customerId);
}
