package VietFreshHub.subscription.dto;

import VietFreshHub.subscription.entity.SubscriptionPaymentAction;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionPaymentResponse(
        Long subscriptionPaymentId,
        Long shopId,
        String shopName,
        Long planId,
        String planName,
        BigDecimal amount,
        SubscriptionPaymentAction action,
        SubscriptionPaymentStatus status,
        boolean autoRenew,
        String transactionReference,
        String note,
        Long reviewedBy,
        Long subscriptionId,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}