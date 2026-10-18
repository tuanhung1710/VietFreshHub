package VietFreshHub.inventory.entity;

import VietFreshHub.inventory.enums.AllocationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_allocations", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class StockAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private AllocationStatus status;
}
