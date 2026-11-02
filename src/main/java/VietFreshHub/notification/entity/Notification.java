package VietFreshHub.notification.entity;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.*;
import lombok.Setter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    public Long getUserId() {
        return user == null ? userId : user.getUserId();
    }

    public void setUser(User user) {
        this.user = user;
        this.userId = user == null ? null : user.getUserId();
    }

    public void setUserId(Long userId) {
        this.userId = userId;
        if (userId == null) {
            this.user = null;
        } else if (this.user == null || !userId.equals(this.user.getUserId())) {
            this.user = new User();
            this.user.setUserId(userId);
        }
    }

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
        }
    }

    @Column(name = "type", nullable = false, length = 80, updatable = false)
    private String type;

    @Nationalized
    @Column(name = "title", nullable = false, length = 255, updatable = false)
    private String title;

    @Nationalized
    @Column(name = "content", length = 2000, updatable = false)
    private String content;

    @Column(name = "entity_type", length = 80, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(0)", updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "read_at", columnDefinition = "datetime2(0)")
    private LocalDateTime readAt;

    public Notification(Long userId, String type, String title, String content,
                        String entityType, Long entityId, LocalDateTime createdAt) {
        setUserId(userId);
        this.type = type;
        this.title = title;
        this.content = content;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = createdAt;
    }

    public void markRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
