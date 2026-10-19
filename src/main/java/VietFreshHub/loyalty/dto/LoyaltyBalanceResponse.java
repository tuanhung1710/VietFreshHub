package VietFreshHub.loyalty.dto;

import java.time.LocalDateTime;

public record LoyaltyBalanceResponse(
        Long loyaltyAccountId,
        Long userId,
        Integer pointsBalance,
        Integer totalEarned,
        Integer totalRedeemed,
        LocalDateTime updatedAt
) {
}