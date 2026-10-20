package VietFreshHub.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Append-only audit. Customer pages never expose providerPayload. */
@Entity @Table(schema="dbo", name="payment_transactions")
@Getter @Setter @NoArgsConstructor
public class PaymentTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="transaction_id") private Long transactionId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="payment_id") private Payment payment;
    @Column(name="event_type", length=50, nullable=false) private String eventType;
    @Nationalized @Column(name="provider_transaction_id", length=255) private String providerTransactionId;
    @Column(precision=18, scale=2, nullable=false) private BigDecimal amount;
    @Column(length=30, nullable=false) private String status;
    @Nationalized @Column(name="provider_payload", columnDefinition="nvarchar(max)") private String providerPayload;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @PrePersist void timestamp() { if(createdAt==null) createdAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
