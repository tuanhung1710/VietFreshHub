package VietFreshHub.notification.entity;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(
            name = "title",
            nullable = false,
            columnDefinition = "nvarchar(255)"
    )
    private String title;

    @Column(
            name = "content",
            nullable = false,
            columnDefinition = "nvarchar(2000)"
    )
    private String content;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}