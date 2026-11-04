package VietFreshHub.loyalty.entity;

import java.math.BigDecimal;
import VietFreshHub.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "loyalty_accounts", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loyalty_account_id")
    private Long loyaltyAccountId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "reward_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal rewardBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_earned_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalEarnedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_redeemed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalRedeemedAmount = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                .withNano(0);
    }
}