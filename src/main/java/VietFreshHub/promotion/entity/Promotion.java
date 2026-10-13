package VietFreshHub.promotion.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "promotions", schema = "dbo")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "shop_id")
    private Long shopId;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 18, scale = 2)
    private BigDecimal maxDiscount;

    @Builder.Default
    @Column(name = "minimum_order_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PromotionStatus status = PromotionStatus.INACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
    }
}
