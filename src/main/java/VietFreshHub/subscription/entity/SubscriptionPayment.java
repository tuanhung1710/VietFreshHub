package VietFreshHub.subscription.entity;

import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "subscription_payments", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_payment_id")
    private Long subscriptionPaymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "amount", nullable = false,
            precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private SubscriptionPaymentAction action;

    @Builder.Default
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionPaymentStatus status =
            SubscriptionPaymentStatus.PENDING;

    @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @Nationalized
    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private ShopSubscription subscription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC)
                    .withNano(0);
        }
    }
}