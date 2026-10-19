package VietFreshHub.notification.services;

import VietFreshHub.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;

public interface NotificationService {

    Page<NotificationResponse> getMyNotifications(Long userId, int page);

    void markAsRead(Long userId, Long notificationId);

    void send(
            Long userId,
            String type,
            String title,
            String content,
            String entityType,
            Long entityId
    );
}
