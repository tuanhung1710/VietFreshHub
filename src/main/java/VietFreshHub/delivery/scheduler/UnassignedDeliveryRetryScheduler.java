package VietFreshHub.delivery.scheduler;

import VietFreshHub.delivery.service.UnassignedDeliveryRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnassignedDeliveryRetryScheduler {

    private final UnassignedDeliveryRetryService unassignedDeliveryRetryService;

    @Scheduled(fixedDelayString = "${delivery.assignment.retry-delay-ms:60000}")
    public void retryUnassignedDeliveries() {
        unassignedDeliveryRetryService.retryUnassignedDeliveries();
    }
}
