package VietFreshHub.order.entity;

import VietFreshHub.product.entity.ProductVariant;
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
    @Column(name="shop_order_id", insertable=false, updatable=false) private Long shopOrderId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="variant_id", nullable=false) private ProductVariant variant;
    @Nationalized @Column(name="product_name_snapshot", nullable=false, length=250) private String productNameSnapshot;
    @Nationalized @Column(name="variant_name_snapshot", nullable=false, length=200) private String variantNameSnapshot;
    @Column(name="sku_snapshot", nullable=false, length=100) private String skuSnapshot;
    @Column(name="unit_price", nullable=false, precision=18, scale=2) private BigDecimal unitPrice;
    @Column(nullable=false) private Integer quantity;
    @Column(name="discount_amount", nullable=false, precision=18, scale=2) private BigDecimal discountAmount=BigDecimal.ZERO;
    // SQL Server trigger owns preparation snapshots; never overwrite them on updates.
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name="requires_preparation_check_snapshot", nullable=false, insertable=false, updatable=false)
    private boolean requiresPreparationCheckSnapshot;
    @Nationalized @Column(name="preparation_note_snapshot", length=1000, insertable=false, updatable=false)
    private String preparationNoteSnapshot;
    @Column(name="total_amount", nullable=false, precision=18, scale=2) private BigDecimal totalAmount;
}
