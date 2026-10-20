package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
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

    @EntityGraph(attributePaths = {"delivery", "deliveryStaff"})
    List<DeliveryAssignment> findByDelivery_DeliveryIdInOrderByAssignedAtDescAssignmentIdDesc(List<Long> deliveryIds);

    @Query("""
            select a from DeliveryAssignment a
            join fetch a.deliveryStaff
            where a.delivery.shopOrder.order.orderId = :orderId
              and a.delivery.shopOrder.order.customer.userId = :customerId
              and a.acceptedAt is not null
            order by a.acceptedAt desc, a.assignmentId desc
            """)
    List<DeliveryAssignment> findAcceptedAssignmentsForCustomerOrder(@Param("customerId") Long customerId,
                                                                   @Param("orderId") Long orderId);

    long countByDeliveryStaff_UserIdAndStatusIn(Long userId, List<DeliveryAssignmentStatus> statuses);

    boolean existsByDelivery_DeliveryIdAndStatusIn(Long deliveryId, List<DeliveryAssignmentStatus> statuses);

    Optional<DeliveryAssignment> findTopByDeliveryStaff_UserIdOrderByAssignedAtDescAssignmentIdDesc(Long userId);

    Optional<DeliveryAssignment> findTopByDelivery_DeliveryIdOrderByAssignedAtDescAssignmentIdDesc(Long deliveryId);

    @Query("""
            select distinct a.deliveryStaff.userId from DeliveryAssignment a
            where a.delivery.deliveryId = :deliveryId
              and a.status in :statuses
              and a.assignedAt > :assignedAfter
            order by a.deliveryStaff.userId asc
            """)
    List<Long> findRecentFailedOfferStaffIds(@Param("deliveryId") Long deliveryId,
                                            @Param("statuses") List<DeliveryAssignmentStatus> statuses,
                                            @Param("assignedAfter") LocalDateTime assignedAfter);

    @Query("select a.delivery.deliveryId from DeliveryAssignment a where a.assignmentId = :assignmentId")
    Optional<Long> findDeliveryIdByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Query("""
            select a.delivery.deliveryId from DeliveryAssignment a
            where a.assignmentId = :assignmentId and a.deliveryStaff.userId = :staffId
            """)
    Optional<Long> findDeliveryIdByAssignmentIdAndStaffId(@Param("assignmentId") Long assignmentId,
                                                        @Param("staffId") Long staffId);

    @Query("""
            select a.delivery.shopOrder.order.orderId from DeliveryAssignment a
            where a.assignmentId = :assignmentId and a.deliveryStaff.userId = :staffId
            """)
    Optional<Long> findOrderIdByAssignmentIdAndStaffId(@Param("assignmentId") Long assignmentId,
                                                     @Param("staffId") Long staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryAssignment> findForUpdateByDelivery_DeliveryIdAndStatus(
            Long deliveryId, DeliveryAssignmentStatus status);

    @Query("""
            select a.assignmentId from DeliveryAssignment a
            where a.status = :assignmentStatus
              and a.delivery.status = :deliveryStatus
              and a.delivery.redeliveryRequestedAt is null and a.delivery.redeliveryScheduledAt is null
              and a.assignedAt is not null
              and a.assignedAt <= :threshold
            order by a.assignedAt asc, a.assignmentId asc
            """)
    List<Long> findTimedOutAssignmentIds(@Param("assignmentStatus") DeliveryAssignmentStatus assignmentStatus,
                                         @Param("deliveryStatus") DeliveryStatus deliveryStatus,
                                         @Param("threshold") LocalDateTime threshold);

    @EntityGraph(attributePaths = {"delivery", "delivery.shopOrder", "delivery.shopOrder.order"})
    List<DeliveryAssignment> findByDeliveryStaff_UserIdAndStatusInOrderByAssignedAtAscAssignmentIdAsc(
            Long userId, List<DeliveryAssignmentStatus> statuses);

    @EntityGraph(attributePaths = {
            "delivery",
            "delivery.shopOrder",
            "delivery.shopOrder.order"
    })
    Optional<DeliveryAssignment> findByAssignmentIdAndDeliveryStaff_UserIdAndStatusIn(
            Long assignmentId, Long userId, List<DeliveryAssignmentStatus> statuses);
}
