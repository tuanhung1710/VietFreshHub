package VietFreshHub.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyCodSummary(LocalDate date, BigDecimal amount, long deliveryCount, long unresolvedCount) {
}
