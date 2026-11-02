package VietFreshHub.delivery.service;

import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.entity.DeliveryStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryReturnQueryService {

    private final ShopOrderRepository shopOrders;

    public List<ShopOrder> collectibleCodShopOrders(Long orderId, List<ShopOrder> children) {
        Specification<ShopOrder> returnedFailures = confirmedReturnedFailures()
                .and((root, query, builder) -> builder.equal(root.get("order").get("orderId"), orderId));
        Set<Long> returnedIds = shopOrders.findAll(returnedFailures).stream()
                .map(ShopOrder::getShopOrderId).collect(Collectors.toSet());
        return children.stream()
                .filter(child -> !"CANCELLED".equals(child.getStatus()) && !returnedIds.contains(child.getShopOrderId()))
                .toList();
    }

    public Specification<ShopOrder> confirmedReturnedFailures() {
        return (shopOrder, query, builder) -> {
            Subquery<Long> returned = query.subquery(Long.class);
            Root<DeliveryAssignment> assignment = returned.from(DeliveryAssignment.class);

            Subquery<Long> active = returned.subquery(Long.class);
            Root<DeliveryAssignment> activeAssignment = active.from(DeliveryAssignment.class);
            active.select(activeAssignment.get("assignmentId")).where(
                    builder.equal(activeAssignment.get("delivery"), assignment.get("delivery")),
                    activeAssignment.get("status").in(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED));

            Subquery<Long> newer = returned.subquery(Long.class);
            Root<DeliveryAssignment> newerAssignment = newer.from(DeliveryAssignment.class);
            newer.select(newerAssignment.get("assignmentId")).where(
                    builder.equal(newerAssignment.get("delivery"), assignment.get("delivery")),
                    builder.equal(newerAssignment.get("status"), DeliveryAssignmentStatus.COMPLETED),
                    builder.or(
                            builder.greaterThan(newerAssignment.get("assignedAt"), assignment.get("assignedAt")),
                            builder.and(
                                    builder.equal(newerAssignment.get("assignedAt"), assignment.get("assignedAt")),
                                    builder.greaterThan(newerAssignment.get("assignmentId"), assignment.get("assignmentId")))));

            Subquery<Long> confirmation = returned.subquery(Long.class);
            Root<DeliveryStatusHistory> history = confirmation.from(DeliveryStatusHistory.class);
            confirmation.select(history.get("historyId")).where(
                    builder.equal(history.get("delivery"), assignment.get("delivery")),
                    builder.equal(history.get("status"), DeliveryStatus.FAILED),
                    builder.isNotNull(history.get("changedBy")),
                    builder.equal(history.get("note"), builder.concat(
                            DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX,
                            assignment.get("assignmentId").cast(String.class))));

            returned.select(assignment.get("assignmentId")).where(
                    builder.equal(assignment.get("delivery").get("shopOrder").get("shopOrderId"), shopOrder.get("shopOrderId")),
                    builder.equal(assignment.get("delivery").get("status"), DeliveryStatus.FAILED),
                    builder.equal(assignment.get("status"), DeliveryAssignmentStatus.COMPLETED),
                    builder.isNotNull(assignment.get("acceptedAt")),
                    builder.not(builder.exists(active)),
                    builder.not(builder.exists(newer)),
                    builder.exists(confirmation));
            return builder.and(builder.equal(shopOrder.get("status"), "OUT_FOR_DELIVERY"), builder.exists(returned));
        };
    }
}
