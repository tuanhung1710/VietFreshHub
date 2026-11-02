package VietFreshHub.payout.entity;

import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.shop.entity.Shop;
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
@Table(schema = "dbo", name = "seller_payouts",
        uniqueConstraints = @UniqueConstraint(name = "UQ_seller_payouts_shop_order", columnNames = "shop_order_id"))
public class SellerPayout {

    public enum Status { PENDING, PROCESSING, PAID, FAILED, CANCELLED }
    public enum FundsSource { COD, ONLINE, UNCONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payout_id")
    private Long payoutId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", insertable = false, updatable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_order_id", nullable = false)
    private ShopOrder shopOrder;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "eligible_at", columnDefinition = "datetime2(0)")
    private LocalDateTime eligibleAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "funds_source", nullable = false, length = 20)
    private FundsSource fundsSource = FundsSource.UNCONFIRMED;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "funds_confirmed_at", columnDefinition = "datetime2(0)")
    private LocalDateTime fundsConfirmedAt;

    @Column(name = "funds_confirmed_by")
    private Long fundsConfirmedBy;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "processed_at", columnDefinition = "datetime2(0)")
    private LocalDateTime processedAt;

    @Nationalized
    @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
