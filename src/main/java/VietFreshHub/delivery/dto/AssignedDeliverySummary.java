package VietFreshHub.delivery.dto;

import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AssignedDeliverySummary {

    private Long assignmentId;
    private Long deliveryId;
    private Long shopOrderId;
    private String orderCode;
    private DeliveryAssignmentStatus assignmentStatus;
    private DeliveryStatus deliveryStatus;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
}
