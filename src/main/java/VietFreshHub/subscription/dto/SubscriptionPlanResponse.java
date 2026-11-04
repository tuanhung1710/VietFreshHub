package VietFreshHub.subscription.dto;

import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.entity.PlanStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionPlanResponse(
        Long planId,
        String name,
        String description,
        BigDecimal price,
        BillingPeriod billingPeriod,
        String benefits,
        Integer maxProducts,
        PlanStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}