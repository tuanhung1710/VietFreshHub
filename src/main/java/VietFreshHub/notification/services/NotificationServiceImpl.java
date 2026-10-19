package VietFreshHub.notification.services;

import VietFreshHub.auth.entity.User;
import VietFreshHub.notification.dto.NotificationResponse;
import VietFreshHub.notification.entity.Notification;
import VietFreshHub.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, int page) {
        requireUserId(userId);
        return notificationRepository
                .findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(
                        userId, PageRequest.of(Math.max(page, 0), 20))
                .map(notification -> new NotificationResponse(
                        notification.getNotificationId(),
                        notification.getTitle(),
                        notification.getContent(),
                        notification.getCreatedAt(),
                        notification.getReadAt()
                ));
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        requireUserId(userId);
        if (notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException("ID thông báo không hợp lệ");
        }
        Notification notification = notificationRepository
                .findByNotificationIdAndUser_UserId(notificationId, userId)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy thông báo của bạn"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID người dùng không hợp lệ");
        }
    }

    @Override
    @Transactional
    public void send(
            Long userId,
            String type,
            String title,
            String content,
            String entityType,
            Long entityId
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "ID người nhận không hợp lệ"
            );
        }

        String validType = requireText(type, 80, "Loại thông báo");
        String validTitle = requireText(title, 255, "Tiêu đề");
        String validContent = requireText(content, 2000, "Nội dung");

        String validEntityType = null;

        if (entityType != null || entityId != null) {
            validEntityType = requireText(
                    entityType,
                    80,
                    "Loại đối tượng liên quan"
            );

            if (entityId == null || entityId <= 0) {
                throw new IllegalArgumentException(
                        "ID đối tượng liên quan không hợp lệ"
                );
            }
        }

        User recipient = entityManager.find(User.class, userId);

        if (recipient == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy người nhận thông báo"
            );
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(validType);
        notification.setTitle(validTitle);
        notification.setContent(validContent);
        notification.setEntityType(validEntityType);
        notification.setEntityId(entityId);

        notificationRepository.save(notification);
    }

    private String requireText(
            String value,
            int maxLength,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " không được để trống"
            );
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " không được vượt quá "
                            + maxLength + " ký tự"
            );
        }

        return normalized;
    }
}
