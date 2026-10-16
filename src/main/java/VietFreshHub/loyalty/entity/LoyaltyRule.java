package VietFreshHub.loyalty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "loyalty_rules", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyRule {

    @Id
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "earning_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal earningAmount;

    @Column(name = "earning_enabled", nullable = false)
    private boolean earningEnabled;

    @Column(name = "redemption_value", nullable = false, precision = 18, scale = 2
    )
    private BigDecimal redemptionValue;

    @Column(name = "min_redemption_points", nullable = false)
    private Integer minRedemptionPoints;

    @Column(name = "max_redemption_percent", nullable = false)
    private Integer maxRedemptionPercent;

    @Column(name = "redemption_enabled", nullable = false)
    private boolean redemptionEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
    }
}