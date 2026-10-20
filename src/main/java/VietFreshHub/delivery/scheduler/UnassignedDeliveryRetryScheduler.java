package VietFreshHub.delivery.scheduler;

import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.delivery.service.DeliveryAssignmentRetryEvent;
import VietFreshHub.delivery.service.UnassignedDeliveryRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class UnassignedDeliveryRetryScheduler {

    private final UnassignedDeliveryRetryService unassignedDeliveryRetryService;
    private final DeliveryAssignmentService deliveryAssignmentService;

    @Scheduled(fixedDelayString = "${delivery.assignment.retry-delay-ms:5000}")
    public void retryUnassignedDeliveries() {
        for (Long assignmentId : deliveryAssignmentService.findTimedOutInitialAssignmentIds()) {
            try {
                deliveryAssignmentService.timeoutInitialAssignment(assignmentId);
            } catch (RuntimeException exception) {
                log.warn("Chưa thể thu hồi phân công hết hạn {}; sẽ thử lại ở chu kỳ sau.", assignmentId);
            }
        }
        retryPendingDeliveries();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void retryAfterCommit(DeliveryAssignmentRetryEvent event) {
        retryPendingDeliveries();
    }

    private void retryPendingDeliveries() {
        try {
            unassignedDeliveryRetryService.retryUnassignedDeliveries();
        } catch (RuntimeException exception) {
            log.warn("Chưa thể phân công đơn đang chờ; scheduler sẽ thử lại ở chu kỳ sau.");
        }
    }
}
