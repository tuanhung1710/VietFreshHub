package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.enums.StockTransactionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    boolean existsByVariantId(Long id);

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
