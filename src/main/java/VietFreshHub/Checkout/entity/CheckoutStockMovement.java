package VietFreshHub.Checkout.entity;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
/** Checkout-specific adapter mapping of the existing inventory_transactions table; replace with TV2's service when integrated. */
@Entity @Table(schema="dbo", name="inventory_transactions")
@Getter @Setter @NoArgsConstructor
public class CheckoutStockMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="transaction_id") private Long transactionId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="variant_id", nullable=false) private ProductVariant variant;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="batch_id") private InventoryBatch batch;
    @Column(name="transaction_type", nullable=false, length=30) private String transactionType;
    @Column(nullable=false) private Integer quantity;
    @Column(name="reference_type", length=50) private String referenceType;
    @Column(name="reference_id") private Long referenceId;
    @Nationalized @Column(length=1000) private String note;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") private User createdBy;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void timestamp() { if(createdAt==null) createdAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
