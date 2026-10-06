package VietFreshHub.Inventory.entity;

import jakarta.persistence.*;
import VietFreshHub.Inventory.enums.AllocationStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="stock_allocations", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class StockAllocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="allocation_id")
    private Long allocationId;
    @Column(name="order_item_id")
    private Long orderItemId;
    @Column(name="batch_id")
    private Long batchId;
    @Column(name="quantity")
    private Integer quantity;
    @Column(name="status", length=20)
    @Enumerated(EnumType.STRING)
    private AllocationStatus status;
}
