package VietFreshHub.Delivery.entity;

import VietFreshHub.Auth.entity.User;
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

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "delivery_assignments")
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_staff_id", nullable = false)
    private User deliveryStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryAssignmentStatus status;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at", columnDefinition = "datetime2(0)")
    private LocalDateTime acceptedAt;

    public DeliveryAssignment(Delivery delivery, User deliveryStaff, LocalDateTime assignedAt) {
        this.delivery = delivery;
        this.deliveryStaff = deliveryStaff;
        this.status = DeliveryAssignmentStatus.ASSIGNED;
        this.assignedAt = assignedAt;
    }

    public void accept(LocalDateTime acceptedAt) {
        this.status = DeliveryAssignmentStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    public void reject() {
        this.status = DeliveryAssignmentStatus.REJECTED;
    }

    public void cancel() {
        this.status = DeliveryAssignmentStatus.CANCELLED;
    }

    public void complete() {
        this.status = DeliveryAssignmentStatus.COMPLETED;
    }
}
