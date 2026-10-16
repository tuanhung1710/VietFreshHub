package VietFreshHub.delivery.entity;

import VietFreshHub.order.entity.ShopOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long deliveryId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_order_id", nullable = false, unique = true)
    private ShopOrder shopOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Setter(AccessLevel.NONE)
    private DeliveryStatus status = DeliveryStatus.UNASSIGNED;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "assigned_at", columnDefinition = "datetime2(0)")
    private LocalDateTime assignedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "picked_up_at", columnDefinition = "datetime2(0)")
    private LocalDateTime pickedUpAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "delivered_at", columnDefinition = "datetime2(0)")
    private LocalDateTime deliveredAt;

    @Nationalized
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "redelivery_requested_at", columnDefinition = "datetime2(0)")
    private LocalDateTime redeliveryRequestedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "redelivery_scheduled_at", columnDefinition = "datetime2(0)")
    private LocalDateTime redeliveryScheduledAt;

    public void markAssigned(LocalDateTime assignedAt) {
        this.status = DeliveryStatus.ASSIGNED;
        this.assignedAt = assignedAt;
    }

    public void releaseAssignment() {
        this.status = DeliveryStatus.UNASSIGNED;
    }

    public void accept() {
        this.status = DeliveryStatus.ACCEPTED;
    }

    public void markPickedUp(LocalDateTime pickedUpAt) {
        this.status = DeliveryStatus.PICKED_UP;
        this.pickedUpAt = pickedUpAt;
    }

    public void startDelivery() {
        this.status = DeliveryStatus.OUT_FOR_DELIVERY;
    }

    public void markFailed(String failureReason) {
        this.status = DeliveryStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void markDelivered(LocalDateTime deliveredAt) {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
