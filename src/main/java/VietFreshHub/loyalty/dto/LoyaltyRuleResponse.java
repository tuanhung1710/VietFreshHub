package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyRuleResponse(
        Long ruleId,
        BigDecimal earningPercent,
        boolean earningEnabled,
        BigDecimal minRedemptionAmount,
        Integer maxRedemptionPercent,
        boolean redemptionEnabled,
        LocalDateTime updatedAt
) {
}