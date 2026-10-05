package VietFreshHub.Inventory.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
/** Read/lock only the stock fields; Order's entity and workflow remain unchanged. */
@Entity @Table(name="order_items",schema="dbo") @Getter @NoArgsConstructor
public class InventoryOrderLine {
    @Id @Column(name="order_item_id") private Long orderItemId;
    @Column(name="shop_order_id") private Long shopOrderId;
    @Column(name="variant_id") private Long variantId;
    @Column(name="quantity") private Integer quantity;
    @Column(name="unit_price",precision=18,scale=2) private BigDecimal unitPrice;
}
