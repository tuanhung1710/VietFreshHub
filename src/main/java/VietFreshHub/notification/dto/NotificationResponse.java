package VietFreshHub.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}
