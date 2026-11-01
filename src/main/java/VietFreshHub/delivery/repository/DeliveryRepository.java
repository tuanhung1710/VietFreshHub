package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    interface RevenueTotal {
        BigDecimal getRevenue();
        Long getDeliveredShopOrders();
    }

    interface RevenueByHour {
        Integer getDeliveryYear();
        Integer getDeliveryMonth();
        Integer getDeliveryDay();
        Integer getDeliveryHour();
        BigDecimal getRevenue();
    }

    @Query("""
            select coalesce(sum(s.totalAmount), 0) as revenue, count(d) as deliveredShopOrders
            from Delivery d join d.shopOrder s
            where d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED
              and s.status = 'COMPLETED' and s.completedAt is not null
              and d.pickedUpAt is not null and d.failureReason is null and d.redeliveryRequestedAt is null
              and d.redeliveryScheduledAt is null
              and d.deliveredAt >= :from and d.deliveredAt < :to
            """)
    RevenueTotal getDeliveredRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select year(d.deliveredAt) as deliveryYear, month(d.deliveredAt) as deliveryMonth,
                   day(d.deliveredAt) as deliveryDay, hour(d.deliveredAt) as deliveryHour,
                   sum(s.totalAmount) as revenue
            from Delivery d join d.shopOrder s
            where d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED
              and s.status = 'COMPLETED' and s.completedAt is not null
              and d.pickedUpAt is not null and d.failureReason is null and d.redeliveryRequestedAt is null
              and d.redeliveryScheduledAt is null
              and d.deliveredAt >= :from and d.deliveredAt < :to
            group by year(d.deliveredAt), month(d.deliveredAt), day(d.deliveredAt), hour(d.deliveredAt)
            order by year(d.deliveredAt), month(d.deliveredAt), day(d.deliveredAt), hour(d.deliveredAt)
            """)
    List<RevenueByHour> getDeliveredRevenueByHour(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    @Query("""
            select count(o) from Order o
            where o.status = VietFreshHub.order.entity.Order.Status.COMPLETED
              and o.completedAt >= :from and o.completedAt < :to
              and exists (select d.deliveryId from Delivery d where d.shopOrder.order = o
                  and d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED)
              and not exists (select s.shopOrderId from ShopOrder s left join Delivery d on d.shopOrder = s
                  where s.order = o
                    and (d.deliveryId is null or d.status <> VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED))
            """)
    long countSuccessfullyDeliveredOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select count(o) from Order o
            where o.placedAt >= :from and o.placedAt < :to
              and (o.status = VietFreshHub.order.entity.Order.Status.CANCELLED
                or exists (select d.deliveryId from Delivery d where d.shopOrder.order = o
                    and d.status = VietFreshHub.delivery.entity.DeliveryStatus.FAILED))
            """)
    long countCancelledOrFailedOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Optional<Delivery> findByShopOrder_ShopOrderId(Long shopOrderId);

    @Override
    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Page<Delivery> findAll(Specification<Delivery> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryId(Long deliveryId);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);

    @Query("""
            select d.deliveryId from Delivery d
            where d.status = :status
              and d.shopOrder.status = 'READY_FOR_DELIVERY'
              and d.redeliveryRequestedAt is null and d.redeliveryScheduledAt is null
            order by d.createdAt asc, d.deliveryId asc
            """)
    List<Long> findUnassignedDeliveryIds(@Param("status") DeliveryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryId(Long deliveryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);
}
