package VietFreshHub.Delivery.service;

import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.Delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedeliveryOrchestrationService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final AutoAssignmentService autoAssignmentService;

    @Transactional(readOnly = true)
    public List<Long> findDueRedeliveryIds() {
        return deliveryRepository.findDueRedeliveryIds(DeliveryStatus.REDELIVERY_SCHEDULED, utcNow());
    }

    @Transactional
    public boolean offerRedelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(deliveryId).orElse(null);
        LocalDateTime now = utcNow();
        if (delivery == null || delivery.getStatus() != DeliveryStatus.REDELIVERY_SCHEDULED
                || delivery.getRedeliveryScheduledAt() == null
                || delivery.getRedeliveryScheduledAt().isAfter(now)) {
            return false;
        }
        if (deliveryAssignmentRepository.existsByDelivery_DeliveryIdAndStatusIn(deliveryId,
                List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))) {
            return false;
        }

        DeliveryAssignment previousAssignment = deliveryAssignmentRepository
                .findTopByDelivery_DeliveryIdAndStatusOrderByAssignedAtDescAssignmentIdDesc(
                        deliveryId, DeliveryAssignmentStatus.COMPLETED)
                .orElse(null);
        if (previousAssignment == null || previousAssignment.getAssignedAt() == null
                || previousAssignment.getDeliveryStaff() == null) {
            return false;
        }

        Set<Long> excludedStaffIds = new HashSet<>(deliveryAssignmentRepository.findFailedOfferStaffIdsAfterAssignment(
                deliveryId, List.of(DeliveryAssignmentStatus.REJECTED, DeliveryAssignmentStatus.CANCELLED),
                previousAssignment.getAssignedAt(), previousAssignment.getAssignmentId()));
        if (!excludedStaffIds.isEmpty()) {
            return autoAssignmentService.tryAssignNextStaffForRedelivery(deliveryId, excludedStaffIds).isPresent();
        }

        return autoAssignmentService.tryAssignSpecificStaffForRedelivery(
                deliveryId, previousAssignment.getDeliveryStaff().getUserId());
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
