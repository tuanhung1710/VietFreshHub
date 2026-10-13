package VietFreshHub.Inventory.entity;

import VietFreshHub.Order.entity.OrderItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(schema="dbo", name="stock_allocations")
@Getter @Setter @NoArgsConstructor
public class StockAllocation {
    public enum Status { RESERVED, SOLD, RELEASED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="allocation_id") private Long allocationId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_item_id", nullable=false) private OrderItem orderItem;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="batch_id", nullable=false) private InventoryBatch batch;
    @Column(nullable=false) private Integer quantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Status status=Status.RESERVED;
}
