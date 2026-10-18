package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.dto.SubscriptionPaymentResponse;
import VietFreshHub.subscription.entity.*;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.repository.SubscriptionPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopSubscriptionServiceImpl
        implements ShopSubscriptionService {

    private final ShopSubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;

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

    @Override
    public SubscriptionPaymentResponse getPayment(
            Long shopId,
            Long paymentId
    ) {
        requireId(shopId);
        requireId(paymentId);

        return paymentRepository
                .findBySubscriptionPaymentIdAndShop_ShopId(
                        paymentId,
                        shopId
                )
                .map(this::toPaymentResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy yêu cầu thanh toán của shop"
                ));
    }

    @Override
    public List<SubscriptionPaymentResponse> getPaymentHistory(
            Long shopId
    ) {
        requireId(shopId);

        return paymentRepository
                .findByShop_ShopIdOrderByCreatedAtDescSubscriptionPaymentIdDesc(
                        shopId
                )
                .stream()
                .map(this::toPaymentResponse)
                .toList();
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

    private SubscriptionPaymentResponse toPaymentResponse(
            SubscriptionPayment payment
    ) {
        return new SubscriptionPaymentResponse(
                payment.getSubscriptionPaymentId(),
                payment.getShop().getShopId(),
                payment.getShop().getShopName(),
                payment.getPlan().getPlanId(),
                payment.getPlan().getName(),
                payment.getAmount(),
                payment.getAction(),
                payment.getStatus(),
                payment.isAutoRenew(),
                payment.getTransactionReference(),
                payment.getNote(),
                payment.getReviewedBy(),
                payment.getSubscription() == null
                        ? null
                        : payment.getSubscription().getSubscriptionId(),
                payment.getCreatedAt(),
                payment.getReviewedAt()
        );
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}