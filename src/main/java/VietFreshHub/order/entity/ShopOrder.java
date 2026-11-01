package VietFreshHub.order.entity;

import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(schema="dbo", name="shop_orders", uniqueConstraints=@UniqueConstraint(columnNames={"order_id","shop_id"}))
@Getter @Setter @NoArgsConstructor
public class ShopOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="shop_order_id") private Long shopOrderId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id", nullable=false) private Order order;
    @Column(name="shop_id", nullable=false) private Long shopId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="shop_id", insertable=false, updatable=false) private Shop shop;
    public void setShop(Shop shop) { this.shop=shop; this.shopId=shop==null ? null : shop.getShopId(); }
    @Column(nullable=false, length=30) private String status="PENDING";
    @Column(nullable=false, precision=18, scale=2) private BigDecimal subtotal=BigDecimal.ZERO;
    @Column(name="discount_total", nullable=false, precision=18, scale=2) private BigDecimal discountTotal=BigDecimal.ZERO;
    @Column(name="shipping_fee", nullable=false, precision=18, scale=2) private BigDecimal shippingFee=BigDecimal.ZERO;
    @Column(name="total_amount", nullable=false, precision=18, scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
    @Column(name="confirmed_at") private LocalDateTime confirmedAt;
    @Column(name="ready_at") private LocalDateTime readyAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="shipping_distance_meters", precision=12, scale=1) private BigDecimal shippingDistanceMeters;
    @OneToMany(mappedBy="shopOrder") private List<OrderItem> items=new ArrayList<>();
    public void confirm() { status="CONFIRMED"; confirmedAt=VietFreshHub.inventory.rule.InventoryRules.now(); }
    public void startPreparing() { status="PREPARING"; }
    public void markReady() { status="READY_FOR_DELIVERY"; readyAt=VietFreshHub.inventory.rule.InventoryRules.now(); }
    public void markOutForDelivery() { status="OUT_FOR_DELIVERY"; }
    public void markCompleted() { status="COMPLETED"; }

@Transient
    public String getShopOrderCode() {
        return VietFreshHub.common.BusinessCodes.format("SOR", shopOrderId);
    }
}
