package VietFreshHub.notification.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.dto.NotificationRow;
import VietFreshHub.notification.entity.Notification;
import VietFreshHub.notification.entity.NotificationDelivery;
import VietFreshHub.notification.repository.NotificationDeliveryRepository;
import VietFreshHub.notification.repository.NotificationRepository;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 15;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final AuthService authService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createInApp(Long userId, String type, String title, String content, String entityType, Long entityId) {
        if (notificationRepository.existsByUserIdAndTypeAndEntityTypeAndEntityId(userId, type, entityType, entityId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        Notification notification = notificationRepository.save(
                new Notification(userId, type, title, content, entityType, entityId, now));
        notificationDeliveryRepository.save(new NotificationDelivery(notification, now));
    }

    @Transactional(readOnly = true)
    public Page<NotificationRow> getNotifications(Authentication authentication, int page) {
        Long userId = authService.getCurrentUserId(authentication);
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "notificationId");
        Page<Notification> notifications = notificationRepository.findInboxByUserId(
                userId, PageRequest.of(page, PAGE_SIZE, sort));
        if (notifications.isEmpty() && notifications.getTotalPages() > 0 && page > 0) {
            notifications = notificationRepository.findInboxByUserId(
                    userId, PageRequest.of(notifications.getTotalPages() - 1, PAGE_SIZE, sort));
        }
        return notifications.map(this::toRow);
    }

    @Transactional(readOnly = true)
    public long countUnread(Authentication authentication) {
        return notificationRepository.countUnreadByUserId(authService.getCurrentUserId(authentication));
    }

    @Transactional(readOnly = true)
    public NotificationRow getOwnedNotification(Long notificationId, Authentication authentication) {
        Long userId = authService.getCurrentUserId(authentication);
        return notificationRepository.findInboxNotification(notificationId, userId)
                .map(this::toRow)
                .orElseThrow(this::notFound);
    }

    @Transactional
    public void markRead(Long notificationId, Authentication authentication) {
        Long userId = authService.getCurrentUserId(authentication);
        Notification notification = notificationRepository.findInboxNotificationForUpdate(notificationId, userId)
                .orElseThrow(this::notFound);
        notification.markRead(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    private NotificationRow toRow(Notification notification) {
        return new NotificationRow(notification.getNotificationId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.getEntityType(), notification.getEntityId(),
                localTime(notification.getCreatedAt()), localTime(notification.getReadAt()));
    }

    private LocalDateTime localTime(LocalDateTime utcTime) {
        return utcTime == null ? null : utcTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE).toLocalDateTime();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo.");
    }
}
