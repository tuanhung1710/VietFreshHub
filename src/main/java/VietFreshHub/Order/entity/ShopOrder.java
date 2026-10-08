package VietFreshHub.Order.entity;

import VietFreshHub.Shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(schema="dbo", name="shop_orders", uniqueConstraints=@UniqueConstraint(columnNames={"order_id","shop_id"}))
@Getter @Setter @NoArgsConstructor
public class ShopOrder {
    public enum Status { PENDING, CONFIRMED, PREPARING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, COMPLETED, CANCELLED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="shop_order_id") private Long shopOrderId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id", nullable=false) private Order order;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shop_id", nullable=false) private Shop shop;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private Status status=Status.PENDING;
    @Column(nullable=false, precision=18, scale=2) private BigDecimal subtotal=BigDecimal.ZERO;
    @Column(name="discount_total", nullable=false, precision=18, scale=2) private BigDecimal discountTotal=BigDecimal.ZERO;
    @Column(name="shipping_fee", nullable=false, precision=18, scale=2) private BigDecimal shippingFee=BigDecimal.ZERO;
    @Column(name="total_amount", nullable=false, precision=18, scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
    @Column(name="confirmed_at") private LocalDateTime confirmedAt;
    @Column(name="ready_at") private LocalDateTime readyAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @OneToMany(mappedBy="shopOrder") private List<OrderItem> items=new ArrayList<>();
}
