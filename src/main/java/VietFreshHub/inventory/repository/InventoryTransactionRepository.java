package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.enums.StockTransactionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    boolean existsByVariantId(Long id);

    interface StockBalance {
        Long getTransactionId();

        Long getQuantityOnHandAfter();

        Long getReservedQuantityAfter();
    }

    /** Reconstruct each balance from the current batch, excluding all later movements. */
    @Query(value = """
            WITH batch_history AS (
                SELECT t.transaction_id,
                    CAST(b.quantity_on_hand AS BIGINT) - COALESCE(
                        SUM(CASE WHEN t.transaction_type IN
                            ('RESTOCK', 'SALE', 'RETURN', 'ADJUSTMENT', 'EXPIRED', 'DAMAGED')
                            THEN CAST(t.quantity AS BIGINT) ELSE CAST(0 AS BIGINT) END)
                        OVER (PARTITION BY t.batch_id ORDER BY t.created_at DESC, t.transaction_id DESC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS quantity_on_hand_after,
                    CAST(b.reserved_quantity AS BIGINT) - COALESCE(
                        SUM(CASE WHEN t.transaction_type IN ('RESERVE', 'RELEASE', 'SALE')
                            THEN CAST(t.quantity AS BIGINT) ELSE CAST(0 AS BIGINT) END)
                        OVER (PARTITION BY t.batch_id ORDER BY t.created_at DESC, t.transaction_id DESC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS reserved_quantity_after
                FROM dbo.inventory_transactions t
                JOIN dbo.inventory_batches b ON b.batch_id=t.batch_id AND b.variant_id=t.variant_id
                JOIN dbo.product_variants v ON v.variant_id=b.variant_id
                JOIN dbo.products p ON p.product_id=v.product_id
                WHERE p.shop_id=:shop AND t.batch_id IN (
                    SELECT selected.batch_id FROM dbo.inventory_transactions selected
                    WHERE selected.transaction_id IN (:ids) AND selected.batch_id IS NOT NULL
                )
            )
            SELECT transaction_id AS transactionId,
                quantity_on_hand_after AS quantityOnHandAfter,
                reserved_quantity_after AS reservedQuantityAfter
            FROM batch_history WHERE transaction_id IN (:ids)
            """, nativeQuery = true)
    List<StockBalance> findStockBalances(Long shop, List<Long> ids);

    @Query(
            """
            select t from InventoryTransaction t, ProductVariant v, Product p
            where t.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop
            and (:variant is null or t.variantId=:variant)
            and (:batch is null or t.batchId=:batch)
            and (:type is null or t.transactionType=:type)
            and t.createdAt>=:from and t.createdAt<:until
            and ((:batchCode is null and :transactionCode is null and (lower(v.sku) like lower(concat('%',:q,'%')) or lower(p.name) like lower(concat('%',:q,'%'))))
                 or (:batchCode is not null and t.batchId=:batchCode)
                 or (:transactionCode is not null and t.transactionId=:transactionCode))
            """)
    Page<InventoryTransaction> search(
            Long shop,
            Long variant,
            Long batch,
            StockTransactionType type,
            String q,
            Long batchCode,
            Long transactionCode,
            LocalDateTime from,
            LocalDateTime until,
            Pageable page);
}
