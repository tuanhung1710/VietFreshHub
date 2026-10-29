package VietFreshHub.payout.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "seller_payout_batch_items",
        uniqueConstraints = @UniqueConstraint(name = "UQ_seller_payout_batch_items_batch_payout",
                columnNames = {"batch_id", "payout_id"}))
public class SellerPayoutBatchItem {

    public enum Status { PENDING, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_item_id")
    private Long batchItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SellerPayoutBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private SellerPayout payout;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Nationalized
    @Column(name = "shop_name_snapshot", nullable = false, length = 200)
    private String shopNameSnapshot;

    @Nationalized
    @Column(name = "bank_name_snapshot", nullable = false, length = 100)
    private String bankNameSnapshot;

    @Column(name = "bank_code_snapshot", nullable = false, length = 30)
    private String bankCodeSnapshot;

    @Column(name = "account_number_snapshot", nullable = false, length = 50)
    private String accountNumberSnapshot;

    @Nationalized
    @Column(name = "account_holder_snapshot", nullable = false, length = 150)
    private String accountHolderSnapshot;

    @Column(name = "transfer_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal transferAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Nationalized
    @Column(name = "transfer_reference", length = 255)
    private String transferReference;

    @Nationalized
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "paid_at", columnDefinition = "datetime2(0)")
    private LocalDateTime paidAt;

    @Column(name = "result_by")
    private Long resultBy;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
