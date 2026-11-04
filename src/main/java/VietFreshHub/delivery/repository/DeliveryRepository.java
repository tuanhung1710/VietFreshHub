package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    interface RevenueByHour {
        Integer getDeliveryYear();
        Integer getDeliveryMonth();
        Integer getDeliveryDay();
        Integer getDeliveryHour();
        BigDecimal getRevenue();
    }

    interface DeliveredOrderTotals {
        BigDecimal getProductValueAfterDiscount();
        BigDecimal getShippingFees();
    }

    @Query("""
            select coalesce(sum(s.totalAmount), 0)
            from Delivery d join d.shopOrder s
            where d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED
              and s.status = 'COMPLETED' and s.completedAt is not null
              and d.pickedUpAt is not null and d.failureReason is null and d.redeliveryRequestedAt is null
              and d.redeliveryScheduledAt is null
              and d.deliveredAt >= :from and d.deliveredAt < :to
            """)
    BigDecimal getDeliveredRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select coalesce(sum(s.subtotal - s.discountTotal), 0) as productValueAfterDiscount,
                   coalesce(sum(s.shippingFee), 0) as shippingFees
            from Delivery d join d.shopOrder s
            where d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED
              and s.status = 'COMPLETED' and s.completedAt is not null
              and d.pickedUpAt is not null and d.failureReason is null and d.redeliveryRequestedAt is null
              and d.redeliveryScheduledAt is null
              and d.deliveredAt >= :from and d.deliveredAt < :to
            """)
    DeliveredOrderTotals getDeliveredOrderTotals(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

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

    Optional<Delivery> findByShopOrder_ShopOrderId(Long shopOrderId);

    List<Delivery> findByShopOrder_ShopOrderIdInOrderByDeliveryIdAsc(List<Long> shopOrderIds);

    @Override
    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Page<Delivery> findAll(Specification<Delivery> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryId(Long deliveryId);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);

    @Override
    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    List<Delivery> findAll(Specification<Delivery> specification, Sort sort);

    static Specification<Delivery> availableForClaim() {
        return (root, query, builder) -> {
            var activeAssignment = query.subquery(Long.class);
            var assignment = activeAssignment.from(DeliveryAssignment.class);
            activeAssignment.select(assignment.get("assignmentId")).where(
                    builder.equal(assignment.get("delivery").get("deliveryId"), root.get("deliveryId")),
                    assignment.get("status").in(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED));
            return builder.and(
                    builder.equal(root.get("status"), DeliveryStatus.UNASSIGNED),
                    builder.equal(root.get("shopOrder").get("status"), "READY_FOR_DELIVERY"),
                    builder.isNull(root.get("redeliveryRequestedAt")),
                    builder.isNull(root.get("redeliveryScheduledAt")),
                    builder.not(builder.exists(activeAssignment)));
        };
    }

    @Query("select d.shopOrder.order.orderId from Delivery d where d.deliveryId = :deliveryId and d.shopOrder.shopId = :shopId")
    Optional<Long> findOrderIdByDeliveryIdAndShopId(@Param("deliveryId") Long deliveryId, @Param("shopId") Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryId(Long deliveryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);
}
