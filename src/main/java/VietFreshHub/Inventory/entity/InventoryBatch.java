package VietFreshHub.Inventory.entity;

import jakarta.persistence.*;
import VietFreshHub.Inventory.enums.BatchStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="inventory_batches", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class InventoryBatch {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="batch_id")
    private Long batchId;
    @Column(name="variant_id")
    private Long variantId;
    @Column(name="batch_code", length=100)
    private String batchCode;
    @Column(name="quantity_on_hand")
    private Integer quantityOnHand;
    @Column(name="reserved_quantity")
    private Integer reservedQuantity;
    @Column(name="cost_price", precision=18, scale=2)
    private BigDecimal costPrice;
    @Column(name="received_at")
    private LocalDateTime receivedAt;
    @Column(name="expiry_date")
    private LocalDate expiryDate;
    @Column(name="status", length=20)
    @Enumerated(EnumType.STRING)
    private BatchStatus status;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
