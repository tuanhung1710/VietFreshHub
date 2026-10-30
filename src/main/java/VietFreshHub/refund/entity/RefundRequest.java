package VietFreshHub.refund.entity;

import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "dbo", name = "refund_requests")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_request_id")
    private Long refundRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_order_id", updatable = false)
    private ShopOrder shopOrder;

    @Nationalized
    @Column(name = "bank_name", nullable = false, length = 120, updatable = false)
    private String bankName;

    @Nationalized
    @Column(name = "bank_account_number", nullable = false, length = 50, updatable = false)
    private String bankAccountNumber;

    @Nationalized
    @Column(name = "account_holder_name", nullable = false, length = 150, updatable = false)
    private String accountHolderName;

    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal refundAmount;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "submitted_at", nullable = false, columnDefinition = "datetime2(0)", updatable = false)
    private LocalDateTime submittedAt;

    public RefundRequest(Order order, String bankName, String bankAccountNumber, String accountHolderName,
                         BigDecimal refundAmount, LocalDateTime submittedAt) {
        this.order = order;
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
        this.accountHolderName = accountHolderName;
        this.refundAmount = refundAmount;
        this.submittedAt = submittedAt;
    }

    public RefundRequest(ShopOrder shopOrder, String bankName, String bankAccountNumber, String accountHolderName,
                         BigDecimal refundAmount, LocalDateTime submittedAt) {
        this(shopOrder.getOrder(), bankName, bankAccountNumber, accountHolderName, refundAmount, submittedAt);
        this.shopOrder = shopOrder;
    }
}
