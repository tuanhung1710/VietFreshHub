package VietFreshHub.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(schema = "dbo", name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Nationalized
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Nationalized
    @Column(name = "provider_payload", columnDefinition = "nvarchar(max)")
    private String providerPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void timestamp() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
