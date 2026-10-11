package VietFreshHub.Delivery.dto;

import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
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
