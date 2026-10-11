package VietFreshHub.Delivery.dto;

import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DeliveryDetailResponse {

    private Long assignmentId;
    private Long deliveryId;
    private Long shopOrderId;
    private Long shopId;
    private String orderCode;
    private DeliveryAssignmentStatus assignmentStatus;
    private DeliveryStatus deliveryStatus;
    private LocalDateTime placedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime redeliveryRequestedAt;
    private LocalDateTime redeliveryScheduledAt;
    private String recipientName;
    private String recipientPhone;
    private DeliveryLocationResponse pickupLocation;
    private DeliveryLocationResponse deliveryLocation;
}
