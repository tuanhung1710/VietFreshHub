package VietFreshHub.subscription.dto;

import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.entity.SubscriptionStatus;

import java.time.LocalDateTime;

public record ShopSubscriptionResponse(
        Long subscriptionId,
        Long shopId,
        String shopName,
        Long planId,
        String planName,
        BillingPeriod billingPeriod,
        String benefits,
        Integer maxProducts,
        LocalDateTime startAt,
        LocalDateTime endAt,
        SubscriptionStatus status,
        boolean autoRenew,
        Long previousSubscriptionId,
        LocalDateTime createdAt
) {
}