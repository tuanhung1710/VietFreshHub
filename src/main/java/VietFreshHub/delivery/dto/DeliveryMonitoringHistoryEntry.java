package VietFreshHub.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DeliveryMonitoringHistoryEntry {

    private String statusLabel;
    private LocalDateTime createdAt;
    private String performerName;
    private String note;
}
