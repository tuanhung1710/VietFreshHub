package VietFreshHub.delivery.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AutoAssignmentService {

    public static final int MAX_ACTIVE_ASSIGNMENTS = 5;
    private static final int OFFER_RETRY_COOLDOWN_SECONDS = 60;
    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<DeliveryAssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = List.of(
            DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED);
    private static final List<DeliveryAssignmentStatus> FAILED_OFFER_STATUSES = List.of(
            DeliveryAssignmentStatus.REJECTED, DeliveryAssignmentStatus.CANCELLED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final AuthService authService;
    private final EntityManager entityManager;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final DeliveryNotificationService deliveryNotificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findUnassignedDeliveryIds() {
        return deliveryRepository.findUnassignedDeliveryIds(DeliveryStatus.UNASSIGNED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> autoAssign(Long deliveryId) {
        if (!isAssignmentWindowOpen(LocalDateTime.now(ZoneOffset.UTC))) {
            return Optional.empty();
        }

        List<Long> staffIds = authService.lockActiveDeliveryStaffIdsForAssignment();
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.UNASSIGNED
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null
                || !"READY_FOR_DELIVERY".equals(delivery.getShopOrder().getStatus())
                || deliveryAssignmentRepository.existsByDelivery_DeliveryIdAndStatusIn(
                        deliveryId, ACTIVE_ASSIGNMENT_STATUSES)) {
            return Optional.empty();
        }

        Set<Long> excludedStaffIds = findExcludedStaffIds(delivery, LocalDateTime.now(ZoneOffset.UTC));
        Optional<Long> selectedStaffId = findAvailableStaff(staffIds, excludedStaffIds);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (selectedStaffId.isEmpty() || !isAssignmentWindowOpen(now)) {
            return Optional.empty();
        }

        LocalDateTime assignedAt = now.withNano(0);
        if (now.getNano() != 0) {
            assignedAt = assignedAt.plusSeconds(1);
        }
        User deliveryStaff = entityManager.getReference(User.class, selectedStaffId.get());
        DeliveryAssignment assignment = deliveryAssignmentRepository.save(
                new DeliveryAssignment(delivery, deliveryStaff, assignedAt));
        delivery.markAssigned(assignedAt);
        deliveryStatusHistoryWriter.record(delivery, null, null);
        deliveryNotificationService.notifyAssigned(assignment);
        return selectedStaffId;
    }

    public boolean isAssignmentWindowOpen(LocalDateTime utcTime) {
        LocalTime localTime = utcTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DELIVERY_ZONE).toLocalTime();
        return !localTime.isBefore(LocalTime.of(8, 0)) && localTime.isBefore(LocalTime.of(19, 0));
//        return true;
    }

    private Set<Long> findExcludedStaffIds(Delivery delivery, LocalDateTime now) {
        LocalDateTime assignedAfter = now.minusSeconds(
                DeliveryAssignmentService.OFFER_TIMEOUT_SECONDS + OFFER_RETRY_COOLDOWN_SECONDS);
        Set<Long> excludedStaffIds = new HashSet<>(deliveryAssignmentRepository.findRecentFailedOfferStaffIds(
                delivery.getDeliveryId(), FAILED_OFFER_STATUSES, assignedAfter));

        if (now.isBefore(delivery.getUpdatedAt().plusSeconds(OFFER_RETRY_COOLDOWN_SECONDS + 1))) {
            deliveryAssignmentRepository.findTopByDelivery_DeliveryIdOrderByAssignedAtDescAssignmentIdDesc(
                            delivery.getDeliveryId())
                    .filter(assignment -> FAILED_OFFER_STATUSES.contains(assignment.getStatus()))
                    .ifPresent(assignment -> excludedStaffIds.add(assignment.getDeliveryStaff().getUserId()));
        }
        return excludedStaffIds;
    }

    private Optional<Long> findAvailableStaff(List<Long> staffIds, Set<Long> excludedStaffIds) {
        Long selectedStaffId = null;
        LocalDateTime oldestLastAssignedAt = null;
        Long oldestLastAssignmentId = null;

        for (Long staffId : staffIds) {
            if (excludedStaffIds.contains(staffId)
                    || deliveryAssignmentRepository.countByDeliveryStaff_UserIdAndStatusIn(
                            staffId, ACTIVE_ASSIGNMENT_STATUSES) >= MAX_ACTIVE_ASSIGNMENTS) {
                continue;
            }

            Optional<DeliveryAssignment> lastAssignment = deliveryAssignmentRepository
                    .findTopByDeliveryStaff_UserIdOrderByAssignedAtDescAssignmentIdDesc(staffId);
            if (lastAssignment.isEmpty()) {
                return Optional.of(staffId);
            }

            DeliveryAssignment latest = lastAssignment.get();
            LocalDateTime lastAssignedAt = latest.getAssignedAt();
            if (selectedStaffId == null || lastAssignedAt.isBefore(oldestLastAssignedAt)
                    || (lastAssignedAt.equals(oldestLastAssignedAt)
                    && latest.getAssignmentId() < oldestLastAssignmentId)) {
                selectedStaffId = staffId;
                oldestLastAssignedAt = lastAssignedAt;
                oldestLastAssignmentId = latest.getAssignmentId();
            }
        }

        return Optional.ofNullable(selectedStaffId);
    }
}
