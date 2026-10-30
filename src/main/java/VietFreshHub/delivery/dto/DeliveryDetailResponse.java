package VietFreshHub.delivery.dto;

import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
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
    private String failureReason;
    private String returnStatusLabel;
    private String assignmentStatusLabel;
    private String deliveryStatusLabel;
    private String recipientName;
    private String recipientPhone;
    private DeliveryLocationResponse pickupLocation;
    private DeliveryLocationResponse deliveryLocation;
    private String shopName;
    private BigDecimal cashToCollect;
    private BigDecimal totalAmount;
    private String paymentStatusLabel;
}
