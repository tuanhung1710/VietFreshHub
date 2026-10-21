package VietFreshHub.subscription;

import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.entity.*;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.scheduler.SubscriptionReminderScheduler;
import VietFreshHub.subscription.services.SubscriptionReminderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionReminderTest {
    private final EntityManager em = mock(EntityManager.class);
    private final ShopMemberRepository members = mock(ShopMemberRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService sender = mock(NotificationService.class);
    private final SubscriptionReminderService service = new SubscriptionReminderService(em, members, notifications, sender);
    private ShopSubscription subscription;

    @BeforeEach
    void setUp() {
        var shop = new Shop();
        shop.setShopId(2L);
        var now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        subscription = ShopSubscription.builder().subscriptionId(1L).shop(shop)
                .plan(SubscriptionPlan.builder().name("Pro").build())
                .status(SubscriptionStatus.ACTIVE).startAt(now.minusDays(1)).endAt(now.plusDays(2)).build();
        when(em.find(ShopSubscription.class, 1L)).thenReturn(subscription);
        when(members.findActiveOwnerUserIds(2L)).thenReturn(List.of(7L, 8L));
    }

    @Test
    void sendsToOwnersOnceIncludingAfterServiceRestart() {
        var sent = new java.util.HashSet<Long>();
        when(notifications.existsByUser_UserIdAndTypeAndEntityTypeAndEntityId(
                anyLong(), eq("SUBSCRIPTION_EXPIRING"), eq("SHOP_SUBSCRIPTION"), eq(1L)))
                .thenAnswer(call -> sent.contains(call.<Long>getArgument(0)));
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; }).when(sender)
                .send(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
        service.remind(1L);
        service.remind(1L);
        new SubscriptionReminderService(em, members, notifications, sender).remind(1L);
        String localEnd = subscription.getEndAt().plusHours(7)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        for (Long owner : List.of(7L, 8L)) {
            verify(sender).send(eq(owner), eq("SUBSCRIPTION_EXPIRING"), eq("Gói dịch vụ sắp hết hạn"),
                    contains(localEnd + " (giờ Việt Nam)"), eq("SHOP_SUBSCRIPTION"), eq(1L));
        }
        verifyNoMoreInteractions(sender);
    }

    @Test
    void skipsCancelledExpiredFutureAndMoreThan72Hours() {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        service.remind(1L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        service.remind(1L);
        subscription.setEndAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(4));
        service.remind(1L);
        subscription.setEndAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        subscription.setStartAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        service.remind(1L);
        verifyNoInteractions(members, notifications, sender);
    }

    @Test
    void refreshesAfterShopLockAndUsesUpdatedStatus() {
        doAnswer(call -> { subscription.setStatus(SubscriptionStatus.CANCELLED); return null; })
                .when(em).refresh(subscription);
        service.remind(1L);
        var order = inOrder(em);
        order.verify(em).find(ShopSubscription.class, 1L);
        order.verify(em).find(Shop.class, 2L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(subscription);
        verifyNoInteractions(sender, members);
    }

    @Test
    void missingSubscriptionReturnsAndMissingOwnersCanBeAddedBeforeNextRun() {
        service.remind(99L);
        verifyNoInteractions(sender, members);
        when(members.findActiveOwnerUserIds(2L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.remind(1L));
        assertDoesNotThrow(() -> service.remind(1L));
        verifyNoInteractions(sender, notifications);
        when(members.findActiveOwnerUserIds(2L)).thenReturn(List.of(7L));
        service.remind(1L);
        verify(sender).send(eq(7L), eq("SUBSCRIPTION_EXPIRING"), anyString(), anyString(), eq("SHOP_SUBSCRIPTION"), eq(1L));
    }

    @Test
    void schedulerQueries72HourWindowAndContinuesAfterFailure() {
        var repository = mock(ShopSubscriptionRepository.class);
        var reminder = mock(SubscriptionReminderService.class);
        when(repository.findExpiringSubscriptionIds(eq(SubscriptionStatus.ACTIVE), any(), any()))
                .thenAnswer(call -> {
                    LocalDateTime now = call.getArgument(1);
                    assertEquals(now.plusHours(72), call.getArgument(2));
                    assertEquals(0, now.getNano());
                    return List.of(1L, 2L);
                });
        doThrow(new IllegalStateException("No owner")).when(reminder).remind(1L);
        new SubscriptionReminderScheduler(repository, reminder).sendExpiryReminders();
        verify(reminder).remind(1L);
        verify(reminder).remind(2L);
    }
}
