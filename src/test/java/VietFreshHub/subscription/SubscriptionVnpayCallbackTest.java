package VietFreshHub.subscription;

import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.payment.vnpay.VnpayGateway;
import VietFreshHub.payment.vnpay.VnpayProperties;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.entity.SubscriptionPayment;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import VietFreshHub.subscription.entity.SubscriptionPaymentAction;
import VietFreshHub.subscription.entity.SubscriptionPlan;
import VietFreshHub.subscription.entity.ShopSubscription;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.repository.SubscriptionPaymentRepository;
import VietFreshHub.subscription.repository.SubscriptionPlanRepository;
import VietFreshHub.subscription.services.ShopSubscriptionServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionVnpayCallbackTest {
    private final ShopSubscriptionRepository subscriptions = mock(ShopSubscriptionRepository.class);
    private final SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final ShopMemberRepository members = mock(ShopMemberRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final VnpayProperties properties = new VnpayProperties();
    private final VnpayGateway gateway = new VnpayGateway(properties);
    private final ShopSubscriptionServiceImpl service = new ShopSubscriptionServiceImpl(subscriptions, payments,
            plans, entityManager, mock(Validator.class), mock(NotificationService.class), members, gateway, properties);
    private SubscriptionPayment payment;

    @BeforeEach
    void setup() {
        properties.setHashSecret("test-secret");
        Shop shop = new Shop();
        shop.setShopId(4L);
        payment = new SubscriptionPayment();
        payment.setSubscriptionPaymentId(41L);
        payment.setShop(shop);
        payment.setAmount(new BigDecimal("125000.00"));
        payment.setTransactionReference("VFS41");
        payment.setStatus(SubscriptionPaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(payments.findByTransactionReference("VFS41")).thenReturn(Optional.of(payment));
        when(payments.findById(41L)).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(41L)).thenReturn(Optional.of(payment));
        when(entityManager.find(eq(Shop.class), eq(4L), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(shop);
        when(subscriptions.findByShop_ShopIdAndStatus(4L, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptions.saveAndFlush(any(ShopSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(members.findActiveOwnerUserIds(4L)).thenReturn(List.of());
    }

    @Test
    void invalidSignatureDoesNotReadOrActivatePayment() {
        assertTrue(service.handleVnpayCallback(Map.of("vnp_TxnRef", "VFS41")).startsWith("97|"));
        verifyNoInteractions(payments, entityManager, subscriptions);
    }

    @Test
    void mismatchedAmountDoesNotActivatePayment() {
        assertTrue(service.handleVnpayCallback(callback("1", "00")).startsWith("04|"));
        assertEquals(SubscriptionPaymentStatus.PENDING, payment.getStatus());
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void failedPaymentIsRecordedWithoutActivation() {
        assertTrue(service.handleVnpayCallback(callback("12500000", "24")).startsWith("00|"));
        assertEquals(SubscriptionPaymentStatus.FAILED, payment.getStatus());
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void repeatedPaidCallbackDoesNotActivateTwice() {
        payment.setStatus(SubscriptionPaymentStatus.PAID);
        assertEquals("00|Confirmed", service.handleVnpayCallback(callback("12500000", "00")));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void repeatedSuccessfulCallbackAfterActivationDoesNotActivateTwice() {
        SubscriptionPlan plan = activePlan();
        payment.setPlan(plan);
        payment.setAction(SubscriptionPaymentAction.SUBSCRIBE);
        when(plans.findById(2L)).thenReturn(Optional.of(plan));
        Map<String, String> fields = callback("12500000", "00");

        assertEquals("00|Confirmed", service.handleVnpayCallback(fields));
        assertEquals("00|Confirmed", service.handleVnpayCallback(fields));
        verify(subscriptions, times(1)).saveAndFlush(any(ShopSubscription.class));
    }

    @Test
    void expiredPaymentCannotStartAnotherVnpayAttempt() {
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getExpiryMinutes() + 1L));
        when(payments.findBySubscriptionPaymentIdAndShop_ShopId(41L, 4L)).thenReturn(Optional.of(payment));

        assertThrows(IllegalArgumentException.class,
                () -> service.startVnpayPayment(4L, 41L, "127.0.0.1", properties.getReturnUrl()));
    }

    @Test
    void successfulCallbackAfterExpiryIsRecordedButDoesNotActivate() {
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getExpiryMinutes() + 1L));

        assertEquals("00|Expired transaction", service.handleVnpayCallback(callback("12500000", "00")));
        assertEquals(SubscriptionPaymentStatus.FAILED, payment.getStatus());
        assertTrue(payment.getNote().contains("requires manual reconciliation"));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void failedCallbackAfterExpiryIsRecordedWithoutCollectingMoneyFlag() {
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getExpiryMinutes() + 1L));

        assertEquals("00|Recorded failure", service.handleVnpayCallback(callback("12500000", "24")));
        assertEquals(SubscriptionPaymentStatus.FAILED, payment.getStatus());
        assertFalse(payment.getNote().contains("money may have been collected"));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void repeatedCallbackAfterLateExpiryDoesNotOverwriteReviewState() {
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getExpiryMinutes() + 1L));
        Map<String, String> fields = callback("12500000", "00");

        assertEquals("00|Expired transaction", service.handleVnpayCallback(fields));
        assertEquals("02|Already processed", service.handleVnpayCallback(fields));
        assertEquals(SubscriptionPaymentStatus.FAILED, payment.getStatus());
        verify(subscriptions, never()).saveAndFlush(any(ShopSubscription.class));
    }

    @Test
    void unknownTransactionReferenceIsRejected() {
        Map<String, String> fields = callback("12500000", "00");
        fields.put("vnp_TxnRef", "VFS9999");
        fields.put("vnp_SecureHash", gateway.sign(fields, properties.getHashSecret()));

        assertEquals("01|Unknown transaction", service.handleVnpayCallback(fields));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void verifiedPaymentActivatesSubscription() {
        SubscriptionPlan plan = activePlan();
        payment.setPlan(plan);
        payment.setAction(SubscriptionPaymentAction.SUBSCRIBE);
        when(plans.findById(2L)).thenReturn(Optional.of(plan));

        assertEquals("00|Confirmed", service.handleVnpayCallback(callback("12500000", "00")));
        assertEquals(SubscriptionPaymentStatus.PAID, payment.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, payment.getSubscription().getStatus());
        verify(subscriptions).saveAndFlush(any(ShopSubscription.class));
    }

    @Test
    void verifiedRenewalLinksTheExpiredSubscription() {
        SubscriptionPlan plan = activePlan();
        payment.setPlan(plan);
        payment.setAction(SubscriptionPaymentAction.RENEW);
        when(plans.findById(2L)).thenReturn(Optional.of(plan));
        ShopSubscription previous = ShopSubscription.builder().plan(plan).status(SubscriptionStatus.EXPIRED)
                .endAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2)).build();
        when(subscriptions.findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(4L)).thenReturn(List.of(previous));

        assertEquals("00|Confirmed", service.handleVnpayCallback(callback("12500000", "00")));
        assertEquals(previous, payment.getSubscription().getPreviousSubscription());
        assertTrue(payment.getSubscription().getEndAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private SubscriptionPlan activePlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanId(2L);
        plan.setName("Pro");
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setPrice(new BigDecimal("125000.00"));
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        return plan;
    }

    private Map<String, String> callback(String amount, String response) {
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_TxnRef", "VFS41");
        fields.put("vnp_Amount", amount);
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_ResponseCode", response);
        fields.put("vnp_TransactionStatus", response);
        fields.put("vnp_SecureHash", gateway.sign(fields, properties.getHashSecret()));
        return fields;
    }
}
