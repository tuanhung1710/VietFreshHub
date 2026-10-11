package VietFreshHub.Delivery.service;

import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnassignedDeliveryRetryService {

    private final DeliveryRepository deliveryRepository;
    private final AutoAssignmentService autoAssignmentService;

    public void retryUnassignedDeliveries() {
        List<Delivery> deliveries = deliveryRepository.findByStatusOrderByCreatedAtAscDeliveryIdAsc(
                DeliveryStatus.UNASSIGNED);

        for (Delivery delivery : deliveries) {
            try {
                if (autoAssignmentService.autoAssign(delivery.getDeliveryId()).isEmpty()) {
                    return;
                }
            } catch (ResponseStatusException exception) {
                if (!HttpStatus.CONFLICT.equals(exception.getStatusCode())) {
                    throw exception;
                }
            }
        }
    }
}
