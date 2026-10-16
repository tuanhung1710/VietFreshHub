package VietFreshHub.inventory.entity;

import VietFreshHub.inventory.enums.StockTransactionType;

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

import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "transaction_type", length = 30)
    @Enumerated(EnumType.STRING)
    private StockTransactionType transactionType;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Nationalized
    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @jakarta.persistence.Transient
    public String getTransactionCode() {
        return VietFreshHub.common.BusinessCodes.format("INV", transactionId);
    }
}
