package VietFreshHub.Order.entity;

import VietFreshHub.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(schema="dbo", name="orders")
@Getter @Setter @NoArgsConstructor
public class Order {
    public enum Status { PENDING, CONFIRMED, PROCESSING, PARTIALLY_COMPLETED, COMPLETED, CANCELLED }
    public enum PaymentState { UNPAID, PENDING, PAID, PARTIALLY_REFUNDED, REFUNDED, FAILED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_id")
    private Long orderId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id", nullable=false)
    private User customer;
    @Column(name="order_code", nullable=false, length=50, unique=true)
    private String orderCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30)
    private Status status=Status.PENDING;
    @Enumerated(EnumType.STRING) @Column(name="payment_status", nullable=false, length=30)
    private PaymentState paymentStatus=PaymentState.UNPAID;
    @Column(nullable=false, precision=18, scale=2)
    private BigDecimal subtotal=BigDecimal.ZERO;
    @Column(name="discount_total", nullable=false, precision=18, scale=2)
    private BigDecimal discountTotal=BigDecimal.ZERO;
    @Column(name="shipping_fee", nullable=false, precision=18, scale=2)
    private BigDecimal shippingFee=BigDecimal.ZERO;
    @Column(name="grand_total", nullable=false, precision=18, scale=2)
    private BigDecimal grandTotal=BigDecimal.ZERO;
    @Column(name="placed_at", nullable=false, updatable=false)
    private LocalDateTime placedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @PrePersist void timestamp() { if(placedAt==null) placedAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
