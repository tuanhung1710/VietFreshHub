package VietFreshHub.subscription;

import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.entity.*;
import VietFreshHub.subscription.repository.*;
import VietFreshHub.subscription.services.ShopSubscriptionServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopSubscriptionNotificationTest {
    private final ShopSubscriptionRepository subscriptions = mock(ShopSubscriptionRepository.class);
    private final SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ShopMemberRepository members = mock(ShopMemberRepository.class);
    private final ShopSubscriptionServiceImpl service = new ShopSubscriptionServiceImpl(
            subscriptions, payments, plans, em, mock(Validator.class), notifications, members);
    private Shop shop;
    private SubscriptionPlan plan;
    private SubscriptionPayment payment;

    @BeforeEach
    void setUp() {
        shop = new Shop();
        shop.setShopId(1L);
        plan = SubscriptionPlan.builder().planId(2L).name("Pro").price(BigDecimal.TEN)
                .billingPeriod(BillingPeriod.MONTHLY).maxProducts(100).build();
        payment = SubscriptionPayment.builder().subscriptionPaymentId(3L).shop(shop).plan(plan)
                .action(SubscriptionPaymentAction.SUBSCRIBE).status(SubscriptionPaymentStatus.PENDING).build();
        when(em.find(Shop.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(shop);
        when(payments.findById(3L)).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(3L)).thenReturn(Optional.of(payment));
        when(plans.findById(2L)).thenReturn(Optional.of(plan));
        when(members.findActiveOwnerUserIds(1L)).thenReturn(List.of(10L, 11L));
    }

    @Test
    void approvalNotifiesEveryActiveOwnerAndRepeatDoesNotNotifyAgain() {
        when(subscriptions.saveAndFlush(any())).thenAnswer(invocation -> {
            ShopSubscription saved = invocation.getArgument(0);
            saved.setSubscriptionId(4L);
            return saved;
        });
        service.approvePayment(3L, 99L, "TX-1");
        for (Long owner : List.of(10L, 11L)) {
            verify(notifications).send(owner, "SUBSCRIPTION_ACTIVATED", "Gói dịch vụ đã được kích hoạt",
                    "Shop đã đăng ký thành công gói Pro.", "SHOP_SUBSCRIPTION", 4L);
        }
        assertThrows(IllegalArgumentException.class, () -> service.approvePayment(3L, 99L, "TX-1"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void rejectionIncludesNormalizedReasonAndRepeatDoesNotNotifyAgain() {
        service.rejectPayment(3L, 99L, "  Sai số tiền  ");
        for (Long owner : List.of(10L, 11L)) {
            verify(notifications).send(owner, "SUBSCRIPTION_PAYMENT_REJECTED",
                    "Yêu cầu thanh toán gói bị từ chối",
                    "Yêu cầu thanh toán gói Pro đã bị từ chối. Lý do: Sai số tiền",
                    "SUBSCRIPTION_PAYMENT", 3L);
        }
        assertThrows(IllegalArgumentException.class, () -> service.rejectPayment(3L, 99L, "Sai số tiền"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void cancellationNotifiesOwnersAndRepeatDoesNotNotifyAgain() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var subscription = ShopSubscription.builder().subscriptionId(4L).shop(shop).plan(plan)
                .status(SubscriptionStatus.ACTIVE).autoRenew(true)
                .startAt(now.minusDays(1)).endAt(now.plusDays(1)).build();
        when(subscriptions.findBySubscriptionIdAndShop_ShopId(4L, 1L)).thenReturn(Optional.of(subscription));
        service.cancelSubscription(1L, 4L);
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
        assertFalse(subscription.isAutoRenew());
        for (Long owner : List.of(10L, 11L)) {
            verify(notifications).send(owner, "SUBSCRIPTION_CANCELLED", "Gói dịch vụ đã được hủy",
                    "Gói Pro của shop đã được hủy.", "SHOP_SUBSCRIPTION", 4L);
        }
        assertThrows(IllegalArgumentException.class, () -> service.cancelSubscription(1L, 4L));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void expiredActiveSubscriptionIsUnavailableAndHistoryDerivesExpiredWithoutWriting() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var expired = ShopSubscription.builder().subscriptionId(4L).shop(shop).plan(plan)
                .status(SubscriptionStatus.ACTIVE).autoRenew(true)
                .startAt(now.minusDays(5)).endAt(now.minusSeconds(1)).build();
        when(subscriptions.findByShop_ShopIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(expired));
        when(subscriptions.findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(1L)).thenReturn(List.of(expired));
        assertTrue(service.getCurrentSubscription(1L).isEmpty());
        assertEquals(SubscriptionStatus.EXPIRED, service.getSubscriptionHistory(1L).getFirst().status());
        assertEquals(SubscriptionStatus.ACTIVE, expired.getStatus());
        verify(subscriptions, never()).flush(); verify(subscriptions, never()).save(any());
        verifyNoInteractions(notifications);
    }
}
