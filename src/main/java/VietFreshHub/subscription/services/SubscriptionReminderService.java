package VietFreshHub.subscription.services;

import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.subscription.entity.ShopSubscription;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionReminderService {
    private static final String TYPE = "SUBSCRIPTION_EXPIRING";
    private static final String ENTITY_TYPE = "SHOP_SUBSCRIPTION";

    private final EntityManager entityManager;
    private final ShopMemberRepository shopMemberRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Transactional
    public void remind(Long subscriptionId) {
        ShopSubscription subscription = entityManager.find(ShopSubscription.class, subscriptionId);
        if (subscription == null) {
            return;
        }

        Long shopId = subscription.getShop().getShopId();
        // Các lần chạy đồng thời của cùng shop phải chờ nhau.
        entityManager.find(Shop.class, shopId, LockModeType.PESSIMISTIC_WRITE);
        // Đọc lại trạng thái sau khi đã lấy được khóa Shop.
        entityManager.refresh(subscription);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.getStartAt().isAfter(now)
                || !subscription.getEndAt().isAfter(now)
                || subscription.getEndAt().isAfter(now.plusDays(3))) {
            return;
        }

        var ownerIds = shopMemberRepository.findActiveOwnerUserIds(shopId);
        if (ownerIds.isEmpty()) {
            // Not a delivery failure. Do not mark as sent: a later run can find
            // the owner after membership is repaired, within the reminder window.
            log.debug("Bỏ qua nhắc đăng ký {}: shop {} chưa có owner đang hoạt động", subscriptionId, shopId);
            return;
        }

        String endAt = subscription.getEndAt()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        for (Long ownerId : ownerIds) {
            boolean alreadySent = notificationRepository
                    .existsByUser_UserIdAndTypeAndEntityTypeAndEntityId(
                            ownerId, TYPE, ENTITY_TYPE, subscriptionId);
            if (alreadySent) {
                continue;
            }

            notificationService.send(
                    ownerId,
                    TYPE,
                    "Gói dịch vụ sắp hết hạn",
                    "Gói " + subscription.getPlan().getName()
                            + " sẽ hết hạn lúc " + endAt
                            + " (giờ Việt Nam). "
                            + "Sau khi hết hạn, shop áp dụng quyền lợi "
                            + "gói Free nếu chưa có gói mới.",
                    ENTITY_TYPE,
                    subscriptionId
            );
        }
    }
}
