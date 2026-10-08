package VietFreshHub.Delivery.service;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.Delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AutoAssignmentService {

    private static final List<DeliveryAssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = List.of(
            DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final AuthService authService;
    private final EntityManager entityManager;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;

    @Transactional
    public Optional<Long> autoAssign(Long deliveryId) {
        return autoAssignExcluding(deliveryId, null);
    }

    @Transactional
    public Optional<Long> autoAssignExcluding(Long deliveryId, Long excludedDeliveryStaffId) {
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));

        if (delivery.getStatus() != DeliveryStatus.UNASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }

        List<Long> staffIds = authService.lockActiveDeliveryStaffIdsForAssignment();
        Set<Long> excludedStaffIds = excludedDeliveryStaffId == null ? Set.of() : Set.of(excludedDeliveryStaffId);
        Optional<Long> selectedStaffId = findIdleStaff(staffIds, excludedStaffIds);
        if (selectedStaffId.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        createAssignment(delivery, selectedStaffId.get(), now);
        delivery.markAssigned(now);
        deliveryStatusHistoryWriter.record(delivery, null, null);

        return selectedStaffId;
    }

    @Transactional
    public boolean tryAssignSpecificStaffForRedelivery(Long deliveryId, Long deliveryStaffId) {
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.REDELIVERY_SCHEDULED) {
            return false;
        }

        List<Long> staffIds = authService.lockActiveDeliveryStaffIdsForAssignment();
        if (!staffIds.contains(deliveryStaffId)) {
            return false;
        }
        if (deliveryAssignmentRepository.existsByDelivery_DeliveryIdAndStatusIn(
                deliveryId, ACTIVE_ASSIGNMENT_STATUSES)) {
            return false;
        }
        if (deliveryAssignmentRepository.existsByDeliveryStaff_UserIdAndStatusIn(
                deliveryStaffId, ACTIVE_ASSIGNMENT_STATUSES)) {
            return false;
        }

        createAssignment(delivery, deliveryStaffId, LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        return true;
    }

    @Transactional
    public Optional<Long> tryAssignNextStaffForRedelivery(Long deliveryId, Set<Long> excludedStaffIds) {
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.REDELIVERY_SCHEDULED) {
            return Optional.empty();
        }
        if (deliveryAssignmentRepository.existsByDelivery_DeliveryIdAndStatusIn(
                deliveryId, ACTIVE_ASSIGNMENT_STATUSES)) {
            return Optional.empty();
        }

        List<Long> staffIds = authService.lockActiveDeliveryStaffIdsForAssignment();
        Optional<Long> selectedStaffId = findIdleStaff(staffIds, excludedStaffIds);
        if (selectedStaffId.isEmpty()) {
            return Optional.empty();
        }

        createAssignment(delivery, selectedStaffId.get(), LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        return selectedStaffId;
    }

    private Optional<Long> findIdleStaff(List<Long> staffIds, Set<Long> excludedStaffIds) {
        Long selectedStaffId = null;
        LocalDateTime oldestLastAssignedAt = null;

        for (Long staffId : staffIds) {
            if (excludedStaffIds.contains(staffId)) {
                continue;
            }
            if (deliveryAssignmentRepository.existsByDeliveryStaff_UserIdAndStatusIn(staffId, ACTIVE_ASSIGNMENT_STATUSES)) {
                continue;
            }

            Optional<DeliveryAssignment> lastAssignment = deliveryAssignmentRepository
                    .findTopByDeliveryStaff_UserIdOrderByAssignedAtDesc(staffId);
            if (lastAssignment.isEmpty()) {
                return Optional.of(staffId);
            }

            LocalDateTime lastAssignedAt = lastAssignment.get().getAssignedAt();
            if (selectedStaffId == null || lastAssignedAt.isBefore(oldestLastAssignedAt)) {
                selectedStaffId = staffId;
                oldestLastAssignedAt = lastAssignedAt;
            }
        }

        return Optional.ofNullable(selectedStaffId);
    }

    private void createAssignment(Delivery delivery, Long deliveryStaffId, LocalDateTime assignedAt) {
        User deliveryStaff = entityManager.getReference(User.class, deliveryStaffId);
        deliveryAssignmentRepository.save(new DeliveryAssignment(delivery, deliveryStaff, assignedAt));
    }
}
