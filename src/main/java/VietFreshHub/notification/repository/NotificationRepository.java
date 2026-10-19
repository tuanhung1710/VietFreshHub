package VietFreshHub.notification.repository;

import VietFreshHub.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification>
    findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(
            Long userId,
            Pageable pageable
    );

    long countByUser_UserIdAndReadAtIsNull(Long userId);

    boolean existsByUser_UserIdAndTypeAndEntityTypeAndEntityId(
            Long userId,
            String type,
            String entityType,
            Long entityId
    );

    Optional<Notification> findByNotificationIdAndUser_UserId(
            Long notificationId,
            Long userId
    );
}
