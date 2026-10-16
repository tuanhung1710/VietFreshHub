package VietFreshHub.delivery.dto;

import java.time.LocalDateTime;

public record DeliveryMonitoringAssignment(Long assignmentId, String statusLabel, String staffName,
                                           LocalDateTime assignedAt, LocalDateTime acceptedAt, boolean current) {
}
