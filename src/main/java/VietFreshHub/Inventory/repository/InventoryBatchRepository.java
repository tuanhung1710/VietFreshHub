package VietFreshHub.Inventory.repository;

import VietFreshHub.Inventory.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    boolean existsByVariantVariantId(Long variantId);

    /**
     * Calculates real-time available stock for a product variant.
     * Available stock = SUM(quantity_on_hand) - SUM(reserved_quantity)
     */
    @Query("SELECT COALESCE(SUM(CAST(b.quantityOnHand AS long) - b.reservedQuantity), 0) " +
           "FROM InventoryBatch b WHERE b.variant.variantId = :variantId " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate >= :today")
    Long findAvailableStock(@Param("variantId") Long variantId, @Param("today") java.time.LocalDate today);

    default Integer getAvailableStockByVariantId(Long variantId) {
        Long stock = findAvailableStock(variantId, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        return stock == null ? 0 : (int) Math.min(stock, Integer.MAX_VALUE);
    }

    @Query("SELECT b.variant.variantId, SUM(CAST(b.quantityOnHand AS long) - b.reservedQuantity) FROM InventoryBatch b "
            + "WHERE b.variant.variantId IN :variantIds AND b.expiryDate IS NOT NULL AND b.expiryDate >= :today "
            + "GROUP BY b.variant.variantId")
    java.util.List<Object[]> findAvailableStockForVariants(@Param("variantIds") java.util.List<Long> variantIds,
                                                          @Param("today") java.time.LocalDate today);

    default java.util.Map<Long, Integer> getAvailableStocks(java.util.List<Long> variantIds) {
        if (variantIds.isEmpty()) return java.util.Map.of();
        java.util.Map<Long, Integer> stocks = new java.util.HashMap<>();
        for (Object[] row : findAvailableStockForVariants(variantIds,
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) {
            stocks.put(((Number) row[0]).longValue(), (int) Math.min(((Number) row[1]).longValue(), Integer.MAX_VALUE));
        }
        return stocks;
    }
}
