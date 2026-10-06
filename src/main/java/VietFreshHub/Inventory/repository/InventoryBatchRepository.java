package VietFreshHub.Inventory.repository;

import VietFreshHub.Inventory.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /**
     * Calculates real-time available stock for a product variant.
     * Available stock = SUM(quantity_on_hand) - SUM(reserved_quantity)
     */
    @Query("SELECT COALESCE(SUM(b.quantityOnHand - b.reservedQuantity), 0) " +
           "FROM InventoryBatch b WHERE b.variant.variantId = :variantId")
    Integer getAvailableStockByVariantId(@Param("variantId") Long variantId);
}
