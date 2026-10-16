package VietFreshHub.inventory.entity;

import VietFreshHub.inventory.enums.BatchStatus;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_batches", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class InventoryBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

    @Column(name = "reserved_quantity")
    private Integer reservedQuantity;

    @Column(name = "cost_price", precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private BatchStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @jakarta.persistence.Transient
    public String getBatchCode() {
        return VietFreshHub.common.BusinessCodes.format("LOT", batchId);
    }
}
