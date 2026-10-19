package VietFreshHub.subscription.services;

import VietFreshHub.shop.entity.Shop;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.dto.SubscriptionPaymentForm;
import VietFreshHub.subscription.dto.SubscriptionPaymentResponse;
import VietFreshHub.subscription.entity.*;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.repository.SubscriptionPaymentRepository;
import VietFreshHub.subscription.repository.SubscriptionPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final SubscriptionPlanRepository planRepository;
    private final EntityManager entityManager;
    private final Validator validator;
    private final NotificationService notificationService;
    private final ShopMemberRepository shopMemberRepository;

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
    public List<ShopSubscriptionResponse> getSubscriptionHistory(
            Long shopId
    ) {
        requireId(shopId);

        return subscriptionRepository
                .findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(
                        shopId
                )
                .stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionPaymentResponse createPaymentRequest(
            Long shopId,
            SubscriptionPaymentForm form
    ) {
        validateForm(form);

        Shop shop = lockShop(shopId);
        LocalDateTime now = utcNow();

        ShopSubscription current =
                expireAndFindCurrent(shopId, now);

        if (paymentRepository.existsByShop_ShopIdAndStatus(
                shopId,
                SubscriptionPaymentStatus.PENDING
        )) {
            throw new IllegalArgumentException(
                    "Shop đang có yêu cầu thanh toán chờ xử lý"
            );
        }

        SubscriptionPlan plan = findPurchasablePlan(form.getPlanId());

        validateAction(shopId, current, plan, form.getAction());

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .shop(shop)
                .plan(plan)
                .amount(plan.getPrice())
                .action(form.getAction())
                .autoRenew(false)
                .status(SubscriptionPaymentStatus.PENDING)
                .build();

        return toPaymentResponse(paymentRepository.save(payment));
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

    @Override
    @Transactional
    public void cancelSubscription(
            Long shopId,
            Long subscriptionId
    ) {
        requireId(subscriptionId);
        lockShop(shopId);

        if (paymentRepository.existsByShop_ShopIdAndStatus(
                shopId,
                SubscriptionPaymentStatus.PENDING
        )) {
            throw new IllegalArgumentException(
                    "Cần xử lý yêu cầu thanh toán đang chờ trước khi hủy gói"
            );
        }

        ShopSubscription subscription = subscriptionRepository
                .findBySubscriptionIdAndShop_ShopId(
                        subscriptionId,
                        shopId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy đăng ký của shop"
                ));

        LocalDateTime now = utcNow();

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.getStartAt().isAfter(now)
                || !subscription.getEndAt().isAfter(now)) {
            throw new IllegalArgumentException(
                    "Chỉ được hủy gói đang có hiệu lực"
            );
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setAutoRenew(false);

        notifyShopOwners(
                shopId,
                "SUBSCRIPTION_CANCELLED",
                "Gói dịch vụ đã được hủy",
                "Gói " + subscription.getPlan().getName()
                        + " của shop đã được hủy.",
                "SHOP_SUBSCRIPTION",
                subscription.getSubscriptionId()
        );
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ShopSubscriptionResponse> getAllSubscriptions(
            Pageable pageable
    ) {
        return subscriptionRepository.findAll(pageable)
                .map(this::toSubscriptionResponse);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<SubscriptionPaymentResponse> getPaymentsByStatus(
            SubscriptionPaymentStatus status
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Trạng thái thanh toán không hợp lệ"
            );
        }

        return paymentRepository
                .findByStatusOrderByCreatedAtAscSubscriptionPaymentIdAsc(
                        status
                )
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ShopSubscriptionResponse approvePayment(
            Long paymentId,
            Long adminUserId,
            String transactionReference
    ) {
        requireId(paymentId);
        requireId(adminUserId);

        String reference = requireText(
                transactionReference,
                255,
                "Mã giao dịch không hợp lệ"
        );

        // Lấy shopId trước, sau đó khóa theo thứ tự Shop -> Payment.
        SubscriptionPayment existing = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy yêu cầu thanh toán"
                ));

        Long shopId = existing.getShop().getShopId();
        Shop shop = lockShop(shopId);

        SubscriptionPayment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy yêu cầu thanh toán"
                ));

        // Làm mới vì payment có thể đã nằm trong persistence context.
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        requirePending(payment);

        LocalDateTime now = utcNow();
        ShopSubscription current = expireAndFindCurrent(shopId, now);

        SubscriptionPlan plan =
                findPurchasablePlan(payment.getPlan().getPlanId());

        validateAction(shopId, current, plan, payment.getAction());

        ShopSubscription previous = current;

        if (payment.getAction() == SubscriptionPaymentAction.UPGRADE) {
            current.setStatus(SubscriptionStatus.UPGRADED);
            current.setAutoRenew(false);

            // Giải phóng unique index ACTIVE trước khi thêm gói mới.
            subscriptionRepository.flush();
        } else if (payment.getAction()
                == SubscriptionPaymentAction.RENEW) {
            previous = latestSubscription(shopId).orElseThrow();
        }

        ShopSubscription subscription = ShopSubscription.builder()
                .shop(shop)
                .plan(plan)
                .startAt(now)
                .endAt(calculateEndAt(now, plan.getBillingPeriod()))
                .status(SubscriptionStatus.ACTIVE)
                .autoRenew(false)
                .previousSubscription(previous)
                .build();

        ShopSubscription saved =
                subscriptionRepository.saveAndFlush(subscription);

        payment.setStatus(SubscriptionPaymentStatus.PAID);
        payment.setSubscription(saved);
        payment.setTransactionReference(reference);
        payment.setReviewedBy(adminUserId);
        payment.setReviewedAt(now);

        String actionText = switch (payment.getAction()) {
            case SUBSCRIBE -> "đăng ký";
            case RENEW -> "gia hạn";
            case UPGRADE -> "nâng cấp";
        };

        notifyShopOwners(
                shopId,
                "SUBSCRIPTION_ACTIVATED",
                "Gói dịch vụ đã được kích hoạt",
                "Shop đã " + actionText
                        + " thành công gói " + plan.getName() + ".",
                "SHOP_SUBSCRIPTION",
                saved.getSubscriptionId()
        );

        return toSubscriptionResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void rejectPayment(
            Long paymentId,
            Long adminUserId,
            String reason
    ) {
        requireId(paymentId);
        requireId(adminUserId);

        String note = requireText(
                reason,
                1000,
                "Vui lòng nhập lý do, tối đa 1000 ký tự"
        );

        SubscriptionPayment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy yêu cầu thanh toán"
                ));

        requirePending(payment);

        payment.setStatus(SubscriptionPaymentStatus.FAILED);
        payment.setNote(note);
        payment.setReviewedBy(adminUserId);
        payment.setReviewedAt(utcNow());

        notifyShopOwners(
                payment.getShop().getShopId(),
                "SUBSCRIPTION_PAYMENT_REJECTED",
                "Yêu cầu thanh toán gói bị từ chối",
                "Yêu cầu thanh toán gói "
                        + payment.getPlan().getName()
                        + " đã bị từ chối. Lý do: " + note,
                "SUBSCRIPTION_PAYMENT",
                payment.getSubscriptionPaymentId()
        );
    }

    private void notifyShopOwners(
            Long shopId,
            String type,
            String title,
            String content,
            String entityType,
            Long entityId
    ) {
        List<Long> ownerIds =
                shopMemberRepository.findActiveOwnerUserIds(shopId);

        for (Long ownerId : ownerIds) {
            notificationService.send(
                    ownerId,
                    type,
                    title,
                    content,
                    entityType,
                    entityId
            );
        }
    }

    private void validateAction(
            Long shopId,
            ShopSubscription current,
            SubscriptionPlan target,
            SubscriptionPaymentAction action
    ) {
        switch (action) {
            case SUBSCRIBE -> {
                if (current != null) {
                    throw new IllegalArgumentException(
                            "Shop đang có gói còn hiệu lực"
                    );
                }
            }

            case RENEW -> {
                if (current != null) {
                    throw new IllegalArgumentException(
                            "Bản hiện tại chỉ hỗ trợ gia hạn sau khi hết hạn"
                    );
                }

                ShopSubscription previous = latestSubscription(shopId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Shop chưa có gói để gia hạn"
                        ));

                if (previous.getStatus() != SubscriptionStatus.EXPIRED) {
                    throw new IllegalArgumentException(
                            "Chỉ được gia hạn gói đã hết hạn"
                    );
                }

                if (!previous.getPlan().getPlanId()
                        .equals(target.getPlanId())) {
                    throw new IllegalArgumentException(
                            "Gia hạn phải chọn lại đúng gói trước đó"
                    );
                }
            }

            case UPGRADE -> {
                if (current == null) {
                    throw new IllegalArgumentException(
                            "Shop chưa có gói còn hiệu lực để nâng cấp"
                    );
                }

                if (current.getPlan().getPlanId()
                        .equals(target.getPlanId())) {
                    throw new IllegalArgumentException(
                            "Vui lòng chọn gói khác để nâng cấp"
                    );
                }

                if (target.getMaxProducts()
                        <= current.getPlan().getMaxProducts()) {
                    throw new IllegalArgumentException(
                            "Gói nâng cấp phải có giới hạn sản phẩm cao hơn"
                    );
                }
            }
        }
    }

    private Shop lockShop(Long shopId) {
        requireId(shopId);

        Shop shop = entityManager.find(
                Shop.class,
                shopId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (shop == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy cửa hàng"
            );
        }

        return shop;
    }

    private Optional<ShopSubscription> findActive(Long shopId) {
        return subscriptionRepository.findByShop_ShopIdAndStatus(
                shopId,
                SubscriptionStatus.ACTIVE
        );
    }

    private ShopSubscription expireAndFindCurrent(
            Long shopId,
            LocalDateTime now
    ) {
        ShopSubscription subscription =
                findActive(shopId).orElse(null);

        if (subscription == null) {
            return null;
        }

        if (!subscription.getEndAt().isAfter(now)) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setAutoRenew(false);
            subscriptionRepository.flush();
            return null;
        }

        if (subscription.getStartAt().isAfter(now)) {
            throw new IllegalStateException(
                    "Shop có đăng ký ACTIVE chưa đến ngày bắt đầu"
            );
        }

        return subscription;
    }

    private Optional<ShopSubscription> latestSubscription(Long shopId) {
        return subscriptionRepository
                .findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(
                        shopId
                )
                .stream()
                .findFirst();
    }

    private SubscriptionPlan findPurchasablePlan(Long planId) {
        requireId(planId);

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy gói dịch vụ"
                ));

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Gói dịch vụ hiện không mở đăng ký"
            );
        }

        if (plan.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Gói miễn phí được áp dụng mặc định, không cần thanh toán"
            );
        }

        return plan;
    }

    private LocalDateTime calculateEndAt(
            LocalDateTime startAt,
            BillingPeriod period
    ) {
        return switch (period) {
            case MONTHLY -> startAt.plusMonths(1);
            case QUARTERLY -> startAt.plusMonths(3);
            case YEARLY -> startAt.plusYears(1);
        };
    }

    private void requirePending(SubscriptionPayment payment) {
        if (payment.getStatus() != SubscriptionPaymentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Yêu cầu thanh toán đã được xử lý"
            );
        }
    }

    private void validateForm(SubscriptionPaymentForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu yêu cầu không hợp lệ"
            );
        }

        var violations = validator.validate(form);

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getMessage())
                    .sorted()
                    .findFirst()
                    .orElse("Dữ liệu yêu cầu không hợp lệ");

            throw new IllegalArgumentException(message);
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID không hợp lệ");
        }
    }

    private String requireText(
            String value,
            int maxLength,
            String message
    ) {
        if (value == null
                || value.isBlank()
                || value.strip().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
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
