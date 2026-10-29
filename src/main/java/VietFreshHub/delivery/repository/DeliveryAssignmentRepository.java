package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryAssignment> findForUpdateByAssignmentIdAndDeliveryStaff_UserId(Long assignmentId, Long deliveryStaffId);

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
