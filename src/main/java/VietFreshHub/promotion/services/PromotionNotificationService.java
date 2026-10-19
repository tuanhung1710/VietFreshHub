package VietFreshHub.promotion.services;

import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.repository.PromotionRepository;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PromotionNotificationService {
    private static final String TYPE = "PROMOTION_AVAILABLE";
    private static final String ENTITY_TYPE = "PROMOTION";

    private final EntityManager entityManager;
    private final PromotionRepository promotionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Transactional
    public void notifyFollowers(Long promotionId) {
        // Khóa Promotion để các lần chạy đồng thời không gửi trùng.
        Promotion promotion = entityManager.find(
                Promotion.class, promotionId, LockModeType.PESSIMISTIC_WRITE);
        if (promotion == null || promotion.getShopId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (promotion.getStatus() != PromotionStatus.ACTIVE
                || promotion.getStartAt().isAfter(now)
                || !promotion.getEndAt().isAfter(now)) {
            return;
        }

        Shop shop = entityManager.find(Shop.class, promotion.getShopId());
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            return;
        }

        var recipientIds = promotionRepository.findFavoriteShopUserIds(promotion.getShopId());
        for (Long userId : recipientIds) {
            boolean alreadySent = notificationRepository
                    .existsByUser_UserIdAndTypeAndEntityTypeAndEntityId(
                            userId, TYPE, ENTITY_TYPE, promotionId);
            if (alreadySent) {
                continue;
            }

            notificationService.send(
                    userId,
                    TYPE,
                    "Shop yêu thích có khuyến mãi mới",
                    shop.getShopName()
                            + " đang có chương trình " + promotion.getName()
                            + ". Hãy ghé shop để xem ưu đãi "
                            + "và điều kiện áp dụng.",
                    ENTITY_TYPE,
                    promotionId
            );
        }
    }
}
