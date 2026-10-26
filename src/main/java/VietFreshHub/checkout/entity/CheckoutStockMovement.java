package VietFreshHub.checkout.entity;

import VietFreshHub.auth.entity.User;
import VietFreshHub.catalog.entity.CatalogVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Checkout-specific adapter mapping of the existing inventory_transactions table; replace with TV2's service when integrated. */
@Entity
@Table(schema = "dbo", name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutStockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private CatalogVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private CheckoutInventoryBatch batch;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Nationalized
    @Column(length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void timestamp() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        }
    }
}
