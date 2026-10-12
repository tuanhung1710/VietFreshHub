package VietFreshHub.Delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "delivery_status_history")
public class DeliveryStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "changed_by")
    private Long changedBy;

    @Nationalized
    @Column(name = "note", length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    public DeliveryStatusHistory(Delivery delivery, Long changedBy, String note) {
        this.delivery = delivery;
        this.status = delivery.getStatus();
        this.changedBy = changedBy;
        this.note = note;
    }
}
