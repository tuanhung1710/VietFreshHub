package VietFreshHub.loyalty.dto;

import VietFreshHub.loyalty.entity.LoyaltyTransactionType;

import java.time.LocalDateTime;

public record LoyaltyTransactionResponse(
        Long loyaltyTransactionId,
        Long orderId,
        String orderCode,
        LoyaltyTransactionType transactionType,
        Integer points,
        Integer balanceAfter,
        String note,
        LocalDateTime createdAt
) {
}