package VietFreshHub.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_delivery_id")
    private Long notificationDeliveryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @Column(name = "channel", nullable = false, updatable = false)
    private String channel;

    @Column(name = "status", nullable = false, updatable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "sent_at", columnDefinition = "datetime2(0)", updatable = false)
    private LocalDateTime sentAt;

    public NotificationDelivery(Notification notification, LocalDateTime sentAt) {
        this.notification = notification;
        this.channel = "IN_APP";
        this.status = "SENT";
        this.sentAt = sentAt;
    }
}
