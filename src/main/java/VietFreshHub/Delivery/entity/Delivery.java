package VietFreshHub.Delivery.entity;

import VietFreshHub.Order.entity.ShopOrder;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    @Column(name = "assigned_at", columnDefinition = "datetime2(0)")
    private LocalDateTime assignedAt;

    @Column(name = "picked_up_at", columnDefinition = "datetime2(0)")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at", columnDefinition = "datetime2(0)")
    private LocalDateTime deliveredAt;

    @Nationalized
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime updatedAt;

    @Column(name = "redelivery_requested_at", columnDefinition = "datetime2(0)")
    private LocalDateTime redeliveryRequestedAt;

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

    public void scheduleRedelivery(LocalDateTime requestedAt, LocalDateTime scheduledAt) {
        this.status = DeliveryStatus.REDELIVERY_SCHEDULED;
        this.redeliveryRequestedAt = requestedAt;
        this.redeliveryScheduledAt = scheduledAt;
    }

    public void resumeRedelivery() {
        this.status = DeliveryStatus.OUT_FOR_DELIVERY;
    }

    public void markDelivered(LocalDateTime deliveredAt) {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }
}
