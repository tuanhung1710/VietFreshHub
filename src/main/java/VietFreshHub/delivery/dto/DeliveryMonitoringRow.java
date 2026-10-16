package VietFreshHub.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DeliveryMonitoringRow {

    private Long deliveryId;
    private String orderCode;
    private Long shopOrderId;
    private Long shopId;
    private String shopName;
    private String deliveryStatusLabel;
    private String assignmentTypeLabel;
    private Long assignmentId;
    private String assignmentStatusLabel;
    private String deliveryStaffName;
    private LocalDateTime assignmentAssignedAt;
    private LocalDateTime assignmentAcceptedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime updatedAt;
    private String returnStatusLabel;
    private boolean returnPending;
}
