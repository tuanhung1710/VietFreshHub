package VietFreshHub.subscription.scheduler;

import VietFreshHub.subscription.entity.SubscriptionStatus;
import VietFreshHub.subscription.repository.ShopSubscriptionRepository;
import VietFreshHub.subscription.services.SubscriptionReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionReminderScheduler {
    private final ShopSubscriptionRepository subscriptionRepository;
    private final SubscriptionReminderService reminderService;

    @Scheduled(
            initialDelayString = "${app.subscription-reminder.initial-delay-ms:10000}",
            fixedDelayString = "${app.subscription-reminder.delay-ms:3600000}"
    )
    public void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        var subscriptionIds = subscriptionRepository.findExpiringSubscriptionIds(
                SubscriptionStatus.ACTIVE, now, now.plusDays(3));

        for (Long subscriptionId : subscriptionIds) {
            try {
                reminderService.remind(subscriptionId);
            } catch (RuntimeException ex) {
                log.error("Không gửi được nhắc hết hạn cho đăng ký {}", subscriptionId, ex);
            }
        }
    }
}
