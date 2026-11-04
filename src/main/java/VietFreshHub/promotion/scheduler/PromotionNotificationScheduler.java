package VietFreshHub.promotion.scheduler;

import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.repository.PromotionRepository;
import VietFreshHub.promotion.services.PromotionNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionNotificationScheduler {
    private final PromotionRepository promotionRepository;
    private final PromotionNotificationService notificationService;

    @Scheduled(
            initialDelayString = "${app.promotion-notification.initial-delay-ms:10000}",
            fixedDelayString = "${app.promotion-notification.delay-ms:60000}"
    )
    public void sendPromotionNotifications() {
        var promotionIds = promotionRepository.findOngoingPromotionIds(
                PromotionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));
        for (Long promotionId : promotionIds) {
            try {
                notificationService.notifyFollowers(promotionId);
            } catch (RuntimeException ex) {
                log.error("Không gửi được thông báo cho Promotion {}", promotionId, ex);
            }
        }
    }
}
