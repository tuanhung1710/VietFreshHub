package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyBalanceResponse(
        Long loyaltyAccountId,
        Long userId,
        BigDecimal rewardBalance,
        BigDecimal totalEarnedAmount,
        BigDecimal totalRedeemedAmount,
        LocalDateTime updatedAt
) {
}