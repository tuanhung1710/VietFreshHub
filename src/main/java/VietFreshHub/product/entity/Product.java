package VietFreshHub.product.entity;

import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "products", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "shop_id")
    private Long shopId;

    @Nationalized
    @Column(name = "name", length = 250)
    private String name;

    @Column(name = "slug", length = 300)
    private String slug;

    @Nationalized
    @Column(name = "description", length = 20000)
    private String description;

    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @Column(name = "approval_status", length = 30)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    @Column(name = "moderation_blocked", nullable = false)
    @ColumnDefault("0")
    private boolean moderationBlocked;

    @Nationalized
    @Column(name = "moderation_reason", length = 1000)
    private String moderationReason;

    // Null preserves ambiguous legacy INACTIVE records instead of inventing seller intent.
    @Column(name = "seller_hidden")
    private Boolean sellerHidden;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "requires_preparation_check")
    private Boolean requiresPreparationCheck;

    @Nationalized
    @Column(name = "preparation_note", length = 1000)
    private String preparationNote;

    @Column(name = "expiry_warning_days", nullable = false)
    @ColumnDefault("3")
    private Integer expiryWarningDays = 3;

    public Integer getExpiryWarningDays() {
        return expiryWarningDays == null ? 3 : expiryWarningDays;
    }

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @jakarta.persistence.Transient
    public String getProductCode() {
        return VietFreshHub.common.BusinessCodes.format("PRD", productId);
    }
}
