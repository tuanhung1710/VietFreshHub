package VietFreshHub.promotion;

import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.repository.PromotionRepository;
import VietFreshHub.promotion.scheduler.PromotionNotificationScheduler;
import VietFreshHub.promotion.services.PromotionNotificationService;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionNotificationTest {
    private final EntityManager em = mock(EntityManager.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService sender = mock(NotificationService.class);
    private final PromotionNotificationService service = new PromotionNotificationService(em, promotions, notifications, sender);
    private Promotion promotion;
    private Shop shop;

    @BeforeEach
    void setUp() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        promotion = Promotion.builder().promotionId(1L).shopId(2L).name("Sale")
                .status(PromotionStatus.ACTIVE).startAt(now.minusHours(1)).endAt(now.plusDays(1)).build();
        shop = new Shop();
        shop.setShopId(2L);
        shop.setShopName("Fresh");
        shop.setStatus("ACTIVE");
        when(em.find(Promotion.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(promotion);
        when(em.find(Shop.class, 2L)).thenReturn(shop);
        when(promotions.findFavoriteShopUserIds(2L)).thenReturn(List.of(7L));
    }

    @Test
    void onlyFollowersReceiveOnceAndNewFollowersReceiveDuringPromotion() {
        var sent = new HashSet<Long>();
        when(notifications.existsByUser_UserIdAndTypeAndEntityTypeAndEntityId(
                anyLong(), eq("PROMOTION_AVAILABLE"), eq("PROMOTION"), eq(1L)))
                .thenAnswer(call -> sent.contains(call.<Long>getArgument(0)));
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; }).when(sender)
                .send(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());

        service.notifyFollowers(1L);
        service.notifyFollowers(1L);
        promotion.setStatus(PromotionStatus.INACTIVE);
        service.notifyFollowers(1L);
        promotion.setStatus(PromotionStatus.ACTIVE);
        new PromotionNotificationService(em, promotions, notifications, sender).notifyFollowers(1L);
        verify(sender).send(7L, "PROMOTION_AVAILABLE", "Shop yêu thích có khuyến mãi mới",
                "Fresh đang có chương trình Sale. Hãy ghé shop để xem ưu đãi và điều kiện áp dụng.", "PROMOTION", 1L);
        verifyNoMoreInteractions(sender);

        when(promotions.findFavoriteShopUserIds(2L)).thenReturn(List.of(7L, 8L));
        service.notifyFollowers(1L);
        verify(sender).send(eq(8L), eq("PROMOTION_AVAILABLE"), anyString(), anyString(), eq("PROMOTION"), eq(1L));
        verifyNoMoreInteractions(sender);
    }

    @Test
    void skipsFutureExpiredAndInactivePromotions() {
        promotion.setStartAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        service.notifyFollowers(1L);
        promotion.setStartAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
        promotion.setEndAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        service.notifyFollowers(1L);
        promotion.setEndAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        for (var status : List.of(PromotionStatus.INACTIVE, PromotionStatus.CANCELLED, PromotionStatus.EXPIRED)) {
            promotion.setStatus(status);
            service.notifyFollowers(1L);
        }
        verifyNoInteractions(promotions, notifications, sender);
    }

    @Test
    void skipsMissingPromotionOrShopAndInactiveShop() {
        service.notifyFollowers(99L);
        promotion.setShopId(null);
        service.notifyFollowers(1L);
        promotion.setShopId(2L);
        when(em.find(Shop.class, 2L)).thenReturn(null);
        service.notifyFollowers(1L);
        when(em.find(Shop.class, 2L)).thenReturn(shop);
        shop.setStatus("INACTIVE");
        service.notifyFollowers(1L);
        verifyNoInteractions(promotions, notifications, sender);
    }

    @Test
    void noFollowersProducesNoNotification() {
        when(promotions.findFavoriteShopUserIds(2L)).thenReturn(List.of());
        service.notifyFollowers(1L);
        verifyNoInteractions(notifications, sender);
    }

    @Test
    void schedulerContinuesToNextPromotionAfterFailure() {
        var notifier = mock(PromotionNotificationService.class);
        when(promotions.findOngoingPromotionIds(eq(PromotionStatus.ACTIVE), any()))
                .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("test failure")).when(notifier).notifyFollowers(1L);
        new PromotionNotificationScheduler(promotions, notifier).sendPromotionNotifications();
        verify(notifier).notifyFollowers(1L);
        verify(notifier).notifyFollowers(2L);
    }
}
