package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.InventoryTransaction;
import VietFreshHub.Inventory.enums.StockTransactionType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction,Long> {
    boolean existsByVariantId(Long id);
    @Query("""
        select t from InventoryTransaction t, ProductVariant v, Product p
        where t.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop
        and (:variant is null or t.variantId=:variant)
        and (:type is null or t.transactionType=:type)
        and t.createdAt>=:from and t.createdAt<:until
        and (lower(v.sku) like lower(concat('%',:q,'%')) or lower(p.name) like lower(concat('%',:q,'%'))
         or exists(select b from InventoryBatch b where b.batchId=t.batchId and lower(b.batchCode) like lower(concat('%',:q,'%'))))
        """)
    Page<InventoryTransaction> search(Long shop, Long variant, StockTransactionType type, String q, LocalDateTime from, LocalDateTime until, Pageable page);
}
