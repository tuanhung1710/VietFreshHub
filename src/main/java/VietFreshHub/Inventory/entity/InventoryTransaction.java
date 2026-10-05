package VietFreshHub.Inventory.entity;

import jakarta.persistence.*;
import VietFreshHub.Inventory.enums.StockTransactionType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="inventory_transactions", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class InventoryTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="transaction_id")
    private Long transactionId;
    @Column(name="variant_id")
    private Long variantId;
    @Column(name="batch_id")
    private Long batchId;
    @Column(name="transaction_type", length=30)
    @Enumerated(EnumType.STRING)
    private StockTransactionType transactionType;
    @Column(name="quantity")
    private Integer quantity;
    @Column(name="reference_type", length=50)
    private String referenceType;
    @Column(name="reference_id")
    private Long referenceId;
    @Nationalized
    @Column(name="note", length=1000)
    private String note;
    @Column(name="created_by")
    private Long createdBy;
    @Column(name="created_at")
    private LocalDateTime createdAt;
}
