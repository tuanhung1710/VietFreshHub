package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyRedemptionResponse(
        BigDecimal rewardAmountUsed,
        BigDecimal discountAmount,
        BigDecimal balanceAfterAmount
) {
}