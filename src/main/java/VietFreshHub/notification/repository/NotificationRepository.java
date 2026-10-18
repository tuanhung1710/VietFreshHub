package VietFreshHub.notification.repository;

import VietFreshHub.notification.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByUserIdAndTypeAndEntityTypeAndEntityId(Long userId, String type, String entityType, Long entityId);

    @Query("""
            select n from Notification n
            where n.userId = :userId
              and exists (select d.notificationDeliveryId from NotificationDelivery d
                          where d.notification = n and d.channel = 'IN_APP' and d.status = 'SENT')
            """)
    Page<Notification> findInboxByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.userId = :userId and n.readAt is null
              and exists (select d.notificationDeliveryId from NotificationDelivery d
                          where d.notification = n and d.channel = 'IN_APP' and d.status = 'SENT')
            """)
    long countUnreadByUserId(@Param("userId") Long userId);

    @Query("""
            select n from Notification n
            where n.notificationId = :notificationId and n.userId = :userId
              and exists (select d.notificationDeliveryId from NotificationDelivery d
                          where d.notification = n and d.channel = 'IN_APP' and d.status = 'SENT')
            """)
    Optional<Notification> findInboxNotification(@Param("notificationId") Long notificationId,
                                               @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Notification n
            where n.notificationId = :notificationId and n.userId = :userId
              and exists (select d.notificationDeliveryId from NotificationDelivery d
                          where d.notification = n and d.channel = 'IN_APP' and d.status = 'SENT')
            """)
    Optional<Notification> findInboxNotificationForUpdate(@Param("notificationId") Long notificationId,
                                                        @Param("userId") Long userId);
}
