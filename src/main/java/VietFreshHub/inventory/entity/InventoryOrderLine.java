package VietFreshHub.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Ánh xạ các trường dòng đơn cần đọc và khóa khi xử lý kho, dùng chung dữ liệu đơn hàng. */
@Entity
@Table(name = "order_items", schema = "dbo")
@Getter
@NoArgsConstructor
public class InventoryOrderLine {
    @Id
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price", precision = 18, scale = 2)
    private BigDecimal unitPrice;
}
