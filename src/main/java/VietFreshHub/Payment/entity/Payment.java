package VietFreshHub.Payment.entity;
import VietFreshHub.Order.entity.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
@Entity @Table(schema="dbo", name="payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    public enum Status { PENDING, PROCESSING, PAID, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="payment_id") private Long paymentId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id", nullable=false) private Order order;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="payment_method_id", nullable=false) private PaymentMethod paymentMethod;
    @Column(nullable=false, precision=18, scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private Status status=Status.PENDING;
    @Nationalized @Column(length=100) private String provider;
    @Nationalized @Column(name="transaction_reference", length=255) private String transactionReference;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @PrePersist void timestamp() { if(createdAt==null) createdAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
