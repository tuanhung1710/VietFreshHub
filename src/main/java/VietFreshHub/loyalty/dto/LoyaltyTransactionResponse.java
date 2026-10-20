package VietFreshHub.loyalty.dto;

import java.math.BigDecimal;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;

import java.time.LocalDateTime;

public record LoyaltyTransactionResponse(
        Long loyaltyTransactionId,
        Long orderId,
        String orderCode,
        LoyaltyTransactionType transactionType,
        BigDecimal amount,
        BigDecimal balanceAfterAmount,
        String note,
        LocalDateTime createdAt
) {
}