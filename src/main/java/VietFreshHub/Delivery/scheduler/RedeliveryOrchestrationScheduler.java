package VietFreshHub.Delivery.scheduler;

import VietFreshHub.Delivery.service.DeliveryAssignmentService;
import VietFreshHub.Delivery.service.RedeliveryOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedeliveryOrchestrationScheduler {

    private final RedeliveryOrchestrationService redeliveryOrchestrationService;
    private final DeliveryAssignmentService deliveryAssignmentService;

    @Scheduled(fixedDelayString = "${delivery.redelivery.orchestration-delay-ms:60000}")
    public void orchestrateRedeliveries() {
        for (Long assignmentId : deliveryAssignmentService.findTimedOutRedeliveryAssignmentIds()) {
            deliveryAssignmentService.timeoutRedeliveryAssignment(assignmentId);
        }
        for (Long deliveryId : redeliveryOrchestrationService.findDueRedeliveryIds()) {
            redeliveryOrchestrationService.offerRedelivery(deliveryId);
        }
    }
}
