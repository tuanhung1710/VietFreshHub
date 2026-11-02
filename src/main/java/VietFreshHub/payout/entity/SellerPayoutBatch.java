package VietFreshHub.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "seller_payout_batches",
        uniqueConstraints = @UniqueConstraint(name = "UQ_seller_payout_batches_slot", columnNames = "scheduled_slot"))
public class SellerPayoutBatch {

    public enum Status { READY, PARTIAL, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "scheduled_slot", nullable = false, length = 40)
    private String scheduledSlot;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "scheduled_at", nullable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.READY;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payout_count", nullable = false)
    private int payoutCount;

    @Column(name = "shop_count", nullable = false)
    private int shopCount;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "completed_at", columnDefinition = "datetime2(0)")
    private LocalDateTime completedAt;

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
