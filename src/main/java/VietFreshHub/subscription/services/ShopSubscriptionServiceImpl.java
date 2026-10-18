package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.entity.*;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopSubscriptionServiceImpl
        implements ShopSubscriptionService {

    private final ShopSubscriptionRepository subscriptionRepository;

    @Override
    public Optional<ShopSubscriptionResponse> getCurrentSubscription(
            Long shopId
    ) {
        requireId(shopId);

        LocalDateTime now = utcNow();

        return findActive(shopId)
                .filter(subscription ->
                        !subscription.getStartAt().isAfter(now)
                                && subscription.getEndAt().isAfter(now))
                .map(this::toSubscriptionResponse);
    }

    private Optional<ShopSubscription> findActive(Long shopId) {
        return subscriptionRepository.findByShop_ShopIdAndStatus(
                shopId,
                SubscriptionStatus.ACTIVE
        );
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID không hợp lệ");
        }
    }

    private ShopSubscriptionResponse toSubscriptionResponse(
            ShopSubscription subscription
    ) {
        SubscriptionPlan plan = subscription.getPlan();

        SubscriptionStatus status = subscription.getStatus();

        if (status == SubscriptionStatus.ACTIVE
                && !subscription.getEndAt().isAfter(utcNow())) {
            status = SubscriptionStatus.EXPIRED;
        }

        return new ShopSubscriptionResponse(
                subscription.getSubscriptionId(),
                subscription.getShop().getShopId(),
                subscription.getShop().getShopName(),
                plan.getPlanId(),
                plan.getName(),
                plan.getBillingPeriod(),
                plan.getBenefits(),
                plan.getMaxProducts(),
                subscription.getStartAt(),
                subscription.getEndAt(),
                status,
                subscription.isAutoRenew(),
                subscription.getPreviousSubscription() == null
                        ? null
                        : subscription.getPreviousSubscription()
                        .getSubscriptionId(),
                subscription.getCreatedAt()
        );
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}