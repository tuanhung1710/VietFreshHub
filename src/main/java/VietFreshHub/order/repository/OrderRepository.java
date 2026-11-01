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

    interface VolumeByHour {
        Integer getOrderYear();
        Integer getOrderMonth();
        Integer getOrderDay();
        Integer getOrderHour();
        Long getOrderCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select year(o.placedAt) as orderYear, month(o.placedAt) as orderMonth,
                   day(o.placedAt) as orderDay, hour(o.placedAt) as orderHour, count(o) as orderCount
            from Order o where o.placedAt >= :from and o.placedAt < :to
            group by year(o.placedAt), month(o.placedAt), day(o.placedAt), hour(o.placedAt)
            order by year(o.placedAt), month(o.placedAt), day(o.placedAt), hour(o.placedAt)
            """)
    List<VolumeByHour> findOrderVolumeByHour(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("""
            select o.status as status, count(o) as orderCount
            from Order o where o.placedAt >= :from and o.placedAt < :to
            group by o.status order by o.status
            """)
    List<StatusCount> countOrdersByStatus(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                          @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("select new VietFreshHub.order.dto.OrderListRow(o.orderId,o.orderCode,o.status,o.paymentStatus,o.grandTotal,o.placedAt) "
            + "from Order o where o.customer.userId=:customerId and (:status is null or o.status=:status) "
            + "and (:keyword='' or locate(lower(:keyword),lower(o.orderCode))>0) "
            + "and (:fromDate is null or o.placedAt>=:fromDate) and (:untilDate is null or o.placedAt<:untilDate)")
    org.springframework.data.domain.Page<VietFreshHub.order.dto.OrderListRow> findHistory(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") Order.Status status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,
            @org.springframework.data.repository.query.Param("untilDate") java.time.LocalDateTime untilDate,
            org.springframework.data.domain.Pageable pageable);
    Optional<Order> findByOrderCodeAndCustomerUserId(String code, Long customerId);
    Optional<Order> findByOrderIdAndCustomerUserId(Long orderId, Long customerId);
}
