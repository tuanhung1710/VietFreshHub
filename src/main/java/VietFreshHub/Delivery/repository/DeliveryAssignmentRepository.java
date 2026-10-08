package VietFreshHub.Delivery.repository;

import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryAssignment> findForUpdateByAssignmentIdAndDeliveryStaff_UserId(Long assignmentId, Long deliveryStaffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryAssignment> findForUpdateByAssignmentId(Long assignmentId);

    boolean existsByDeliveryStaff_UserIdAndStatusIn(Long userId, List<DeliveryAssignmentStatus> statuses);

    boolean existsByDelivery_DeliveryIdAndStatusIn(Long deliveryId, List<DeliveryAssignmentStatus> statuses);

    Optional<DeliveryAssignment> findTopByDeliveryStaff_UserIdOrderByAssignedAtDesc(Long userId);

    Optional<DeliveryAssignment> findTopByDelivery_DeliveryIdAndStatusOrderByAssignedAtDescAssignmentIdDesc(
            Long deliveryId, DeliveryAssignmentStatus status);

    @Query("""
            select distinct a.deliveryStaff.userId from DeliveryAssignment a
            where a.delivery.deliveryId = :deliveryId
              and a.status in :statuses
              and (a.assignedAt > :baselineAssignedAt
                   or (a.assignedAt = :baselineAssignedAt and a.assignmentId > :baselineAssignmentId))
            order by a.deliveryStaff.userId asc
            """)
    List<Long> findFailedOfferStaffIdsAfterAssignment(@Param("deliveryId") Long deliveryId,
                                                     @Param("statuses") List<DeliveryAssignmentStatus> statuses,
                                                     @Param("baselineAssignedAt") LocalDateTime baselineAssignedAt,
                                                     @Param("baselineAssignmentId") Long baselineAssignmentId);

    @Query("""
            select a.assignmentId from DeliveryAssignment a
            where a.status = :assignmentStatus
              and a.delivery.status = :deliveryStatus
              and a.assignedAt <= :threshold
            order by a.assignedAt asc, a.assignmentId asc
            """)
    List<Long> findTimedOutRedeliveryAssignmentIds(@Param("assignmentStatus") DeliveryAssignmentStatus assignmentStatus,
                                                  @Param("deliveryStatus") DeliveryStatus deliveryStatus,
                                                  @Param("threshold") LocalDateTime threshold);

    @EntityGraph(attributePaths = {"delivery", "delivery.shopOrder", "delivery.shopOrder.order"})
    List<DeliveryAssignment> findByDeliveryStaff_UserIdAndStatusInOrderByAssignedAtAscAssignmentIdAsc(
            Long userId, List<DeliveryAssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"delivery", "delivery.shopOrder", "delivery.shopOrder.order",
            "delivery.shopOrder.order.orderAddress"})
    Optional<DeliveryAssignment> findByAssignmentIdAndDeliveryStaff_UserIdAndStatusIn(
            Long assignmentId, Long userId, List<DeliveryAssignmentStatus> statuses);
}
