package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyRuleResponse(
        Long ruleId,
        BigDecimal earningAmount,
        boolean earningEnabled,
        BigDecimal redemptionValue,
        Integer minRedemptionPoints,
        Integer maxRedemptionPercent,
        boolean redemptionEnabled,
        LocalDateTime updatedAt
) {
}