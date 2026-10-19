package VietFreshHub.loyalty.entity;

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
    @Column(name = "points_balance", nullable = false)
    private Integer pointsBalance = 0;

    @Builder.Default
    @Column(name = "total_earned", nullable = false)
    private Integer totalEarned = 0;

    @Builder.Default
    @Column(name = "total_redeemed", nullable = false)
    private Integer totalRedeemed = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                .withNano(0);
    }
}