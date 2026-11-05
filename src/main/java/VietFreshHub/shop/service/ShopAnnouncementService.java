package VietFreshHub.shop.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import VietFreshHub.common.PageSizes;

@Service
@RequiredArgsConstructor
public class ShopAnnouncementService {
    public enum Origin { SYSTEM, ADMIN }
    public record Announcement(Long notificationId, String title, String content,
                               java.time.LocalDateTime createdAt, java.time.LocalDateTime readAt) { }
    private final ShopService shops;
    private final ShopRepository repository;
    private final NotificationService notifications;
    private final NotificationRepository inbox;
    private final AuthService authenticationService;

    /** Internal integration point for the admin/system module; source IDs must be stable across retries. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receive(Long shopId, Origin origin, Long announcementId, String title, String content) {
        if (shopId == null || origin == null || announcementId == null || announcementId <= 0
                || title == null || title.isBlank() || title.length() > 255 || content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("Thông báo phải có mã nguồn, tiêu đề tối đa 255 và nội dung tối đa 2000 ký tự.");
        }
        var shop = repository.findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cửa hàng nhận thông báo."));
        if (!"ACTIVE".equals(shop.getStatus())) return;
        for (Long userId : shops.findActiveManagerUserIds(shopId)) {
            notifications.createInApp(userId, origin.name() + "_ANNOUNCEMENT", title.strip(), content.strip(), "ANNOUNCEMENT", announcementId);
        }
    }

    @Transactional(readOnly = true)
    public Page<Announcement> list(Authentication authentication, int page) {
        shops.getManagedShopId(authentication);
        if (page < 1 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        return inbox.findAnnouncements(authenticationService.getCurrentUserId(authentication),
                PageRequest.of(page - 1, PageSizes.DEFAULT, Sort.by(Sort.Direction.DESC, "createdAt", "notificationId")))
                .map(notice -> new Announcement(notice.getNotificationId(), notice.getTitle(), notice.getContent(),
                        localTime(notice.getCreatedAt()), localTime(notice.getReadAt())));
    }

    private java.time.LocalDateTime localTime(java.time.LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(java.time.ZoneOffset.UTC)
                .atZoneSameInstant(VietFreshHub.inventory.rule.InventoryRules.SHOP_ZONE).toLocalDateTime();
    }
}
