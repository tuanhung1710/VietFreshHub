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
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    Optional<Delivery> findByShopOrder_ShopOrderId(Long shopOrderId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryId(Long deliveryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);
}
