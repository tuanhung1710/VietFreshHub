package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyRedemptionResponse(
        Integer pointsUsed,
        BigDecimal discountAmount,
        Integer balanceAfter
) {
}