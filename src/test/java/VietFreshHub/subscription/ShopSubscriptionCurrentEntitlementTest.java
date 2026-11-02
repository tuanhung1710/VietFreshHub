package VietFreshHub.subscription;

import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.entity.ShopSubscription;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.repository.SubscriptionPaymentRepository;
import VietFreshHub.subscription.repository.SubscriptionPlanRepository;
import VietFreshHub.subscription.services.ShopSubscriptionServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopSubscriptionCurrentEntitlementTest {
    @Mock ShopSubscriptionRepository subscriptions;
    @Mock SubscriptionPaymentRepository payments;
    @Mock SubscriptionPlanRepository plans;
    @Mock EntityManager entityManager;
    @Mock Validator validator;
    @Mock NotificationService notifications;
    @Mock ShopMemberRepository shopMembers;

    @InjectMocks ShopSubscriptionServiceImpl service;

    @Test
    void expiredActiveRowIsNotAnEffectiveSubscription() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        ShopSubscription expired = new ShopSubscription();
        expired.setStatus(SubscriptionStatus.ACTIVE);
        expired.setStartAt(now.minusDays(30));
        expired.setEndAt(now.minusDays(1));
        when(subscriptions.findByShop_ShopIdAndStatus(7L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(expired));

        assertTrue(service.getCurrentSubscription(7L).isEmpty());
    }

    @Test
    void cancelledRowIsNotQueriedAsAnEffectiveSubscription() {
        when(subscriptions.findByShop_ShopIdAndStatus(7L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.getCurrentSubscription(7L).isEmpty());

        verify(subscriptions).findByShop_ShopIdAndStatus(7L, SubscriptionStatus.ACTIVE);
    }
}
