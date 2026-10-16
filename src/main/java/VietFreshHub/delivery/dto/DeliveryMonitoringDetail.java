package VietFreshHub.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class DeliveryMonitoringDetail {

    private DeliveryMonitoringRow summary;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private String failureReason;
    private boolean legacyRedelivery;
    private List<DeliveryMonitoringAssignment> assignments;
    private List<DeliveryMonitoringHistoryEntry> history;
}
