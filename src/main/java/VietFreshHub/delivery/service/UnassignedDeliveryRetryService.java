package VietFreshHub.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnassignedDeliveryRetryService {

    private final AutoAssignmentService autoAssignmentService;

    public void retryUnassignedDeliveries() {
        if (!autoAssignmentService.isAssignmentWindowOpen(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        List<Long> deliveryIds = autoAssignmentService.findUnassignedDeliveryIds();

        for (Long deliveryId : deliveryIds) {
            autoAssignmentService.autoAssign(deliveryId);
        }
    }
}
