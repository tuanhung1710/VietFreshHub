package VietFreshHub.Order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "order_items")
public class OrderItem {

    @Id
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Column(name = "product_name_snapshot")
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot")
    private String variantNameSnapshot;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;
}
