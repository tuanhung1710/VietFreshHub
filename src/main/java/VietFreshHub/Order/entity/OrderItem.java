package VietFreshHub.Order.entity;

import VietFreshHub.Product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;

@Entity @Table(schema="dbo", name="order_items")
@Getter @Setter @NoArgsConstructor
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_item_id") private Long orderItemId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shop_order_id", nullable=false) private ShopOrder shopOrder;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="variant_id", nullable=false) private ProductVariant variant;
    @Nationalized @Column(name="product_name_snapshot", nullable=false, length=250) private String productNameSnapshot;
    @Nationalized @Column(name="variant_name_snapshot", nullable=false, length=200) private String variantNameSnapshot;
    @Column(name="sku_snapshot", nullable=false, length=100) private String skuSnapshot;
    @Column(name="unit_price", nullable=false, precision=18, scale=2) private BigDecimal unitPrice;
    @Column(nullable=false) private Integer quantity;
    @Column(name="discount_amount", nullable=false, precision=18, scale=2) private BigDecimal discountAmount=BigDecimal.ZERO;
    @Column(name="total_amount", nullable=false, precision=18, scale=2) private BigDecimal totalAmount;
}
