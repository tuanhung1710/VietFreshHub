package VietFreshHub.order.dto;

import java.time.LocalDateTime;

public record OrderProcessingHistoryEntry(String statusLabel, LocalDateTime createdAt, String note) {
}
