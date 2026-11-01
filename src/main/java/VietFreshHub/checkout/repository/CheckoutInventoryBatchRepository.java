package VietFreshHub.checkout.repository;

import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutInventoryBatchRepository extends JpaRepository<CheckoutInventoryBatch, Long> {
    boolean existsByVariantVariantId(Long variantId);

    /**
     * Calculates real-time available stock for a product variant.
     * Available stock = SUM(quantity_on_hand) - SUM(reserved_quantity)
     */
    @Query("SELECT COALESCE(SUM(CAST(b.quantityOnHand AS long) - b.reservedQuantity), 0) " +
           "FROM CheckoutInventoryBatch b WHERE b.variant.variantId = :variantId " +
           "AND b.status = 'ACTIVE' AND (b.expiryDate IS NULL OR b.expiryDate >= :today)")
    Long findAvailableStock(@Param("variantId") Long variantId, @Param("today") LocalDate today);

    default Integer getAvailableStockByVariantId(Long variantId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Long stock = findAvailableStock(variantId, today);
        if (stock == null) {
            return 0;
        }
        return (int) Math.min(stock, Integer.MAX_VALUE);
    }

    @Query("SELECT b.variant.variantId, SUM(CAST(b.quantityOnHand AS long) - b.reservedQuantity) FROM CheckoutInventoryBatch b "
            + "WHERE b.variant.variantId IN :variantIds AND b.status = 'ACTIVE' AND (b.expiryDate IS NULL OR b.expiryDate >= :today) "
            + "GROUP BY b.variant.variantId")
    List<Object[]> findAvailableStockForVariants(@Param("variantIds") List<Long> variantIds,
                                                @Param("today") LocalDate today);

    default Map<Long, Integer> getAvailableStocks(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> stocks = new HashMap<>();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<Object[]> stockRows = findAvailableStockForVariants(variantIds, today);
        for (Object[] row : stockRows) {
            Number variantIdValue = (Number) row[0];
            Number availableStockValue = (Number) row[1];
            Long variantId = variantIdValue.longValue();
            int availableStock = (int) Math.min(availableStockValue.longValue(), Integer.MAX_VALUE);
            stocks.put(variantId, availableStock);
        }
        return stocks;
    }
}
