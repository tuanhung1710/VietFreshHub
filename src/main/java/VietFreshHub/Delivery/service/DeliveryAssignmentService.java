package VietFreshHub.Delivery.service;

import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.Delivery.repository.DeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final AutoAssignmentService autoAssignmentService;
    private final RedeliveryOrchestrationService redeliveryOrchestrationService;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final AuthService authService;
    private final long redeliveryAssignmentTimeoutMs;

    public DeliveryAssignmentService(DeliveryAssignmentRepository deliveryAssignmentRepository,
                                     DeliveryRepository deliveryRepository,
                                     DeliveryService deliveryService,
                                     AutoAssignmentService autoAssignmentService,
                                     RedeliveryOrchestrationService redeliveryOrchestrationService,
                                     DeliveryStatusHistoryWriter deliveryStatusHistoryWriter,
                                     AuthService authService,
                                     @Value("${delivery.redelivery.assignment-timeout-ms:0}") long redeliveryAssignmentTimeoutMs) {
        this.deliveryAssignmentRepository = deliveryAssignmentRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryService = deliveryService;
        this.autoAssignmentService = autoAssignmentService;
        this.redeliveryOrchestrationService = redeliveryOrchestrationService;
        this.deliveryStatusHistoryWriter = deliveryStatusHistoryWriter;
        this.authService = authService;
        this.redeliveryAssignmentTimeoutMs = redeliveryAssignmentTimeoutMs;
    }

    @Transactional
    public void accept(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);

        Delivery delivery = getDeliveryForUpdate(assignment.getDelivery().getDeliveryId());
        if (delivery.getStatus() == DeliveryStatus.REDELIVERY_SCHEDULED) {
            deliveryService.resumeRedelivery(delivery.getDeliveryId());
        } else {
            deliveryService.accept(delivery.getDeliveryId());
        }
        assignment.accept(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
    }

    @Transactional
    public void reject(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);

        Delivery delivery = getDeliveryForUpdate(assignment.getDelivery().getDeliveryId());
        if (delivery.getStatus() == DeliveryStatus.REDELIVERY_SCHEDULED) {
            assignment.reject();
            redeliveryOrchestrationService.offerRedelivery(delivery.getDeliveryId());
            return;
        }
        deliveryService.releaseAssignment(delivery.getDeliveryId());
        assignment.reject();
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
        autoAssignmentService.autoAssignExcluding(delivery.getDeliveryId(), deliveryStaffId);
    }

    @Transactional(readOnly = true)
    public List<Long> findTimedOutRedeliveryAssignmentIds() {
        if (redeliveryAssignmentTimeoutMs <= 0) {
            return List.of();
        }

        return deliveryAssignmentRepository.findTimedOutRedeliveryAssignmentIds(
                DeliveryAssignmentStatus.ASSIGNED, DeliveryStatus.REDELIVERY_SCHEDULED,
                redeliveryTimeoutThreshold());
    }

    @Transactional
    public void timeoutRedeliveryAssignment(Long assignmentId) {
        if (redeliveryAssignmentTimeoutMs <= 0) {
            return;
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository.findForUpdateByAssignmentId(assignmentId)
                .orElse(null);
        if (assignment == null || assignment.getStatus() != DeliveryAssignmentStatus.ASSIGNED) {
            return;
        }

        Delivery delivery = getDeliveryForUpdate(assignment.getDelivery().getDeliveryId());
        if (delivery.getStatus() != DeliveryStatus.REDELIVERY_SCHEDULED
                || assignment.getAssignedAt() == null
                || assignment.getAssignedAt().isAfter(redeliveryTimeoutThreshold())) {
            return;
        }

        assignment.cancel();
        redeliveryOrchestrationService.offerRedelivery(delivery.getDeliveryId());
    }

    @Transactional
    public void complete(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ACCEPTED);

        Delivery delivery = deliveryRepository.findForUpdateByDeliveryId(assignment.getDelivery().getDeliveryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
        if (delivery.getStatus() != DeliveryStatus.DELIVERED
                && delivery.getStatus() != DeliveryStatus.REDELIVERY_SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể hoàn tất phân công khi giao hàng đã hoàn thành hoặc đã hẹn giao lại.");
        }

        assignment.complete();
    }

    private DeliveryAssignment getAssignmentForUpdate(Long assignmentId, Long deliveryStaffId) {
        return deliveryAssignmentRepository
                .findForUpdateByAssignmentIdAndDeliveryStaff_UserId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
    }

    private Delivery getDeliveryForUpdate(Long deliveryId) {
        return deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    private LocalDateTime redeliveryTimeoutThreshold() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0).minus(Duration.ofMillis(redeliveryAssignmentTimeoutMs));
    }

    private void requireStatus(DeliveryAssignment assignment, DeliveryAssignmentStatus requiredStatus) {
        if (assignment.getStatus() != requiredStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }
    }
}
