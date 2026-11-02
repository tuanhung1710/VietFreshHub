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
    /** Kiểm tra phân loại đã có lịch sử biến động kho hay chưa. */
    boolean existsByVariantId(Long id);

    /** Lịch sử xử lý đơn của đúng lô và SKU, sắp thời gian rồi ID để ổn định thứ tự. */
    @Query("""
            select t from InventoryTransaction t
            where t.batchId=:batch and t.variantId=:variant
            and t.referenceType='ORDER_ITEM' and t.transactionType in :types
            order by t.createdAt asc, t.transactionId asc
            """)
    List<InventoryTransaction> findOrderProcessingHistory(Long batch, Long variant, List<StockTransactionType> types);

    @Query("""
            select t from InventoryTransaction t, ProductVariant v, Product p
            where t.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop
            and (:variant is null or t.variantId=:variant)
            and (:batch is null or t.batchId=:batch)
            and (:type is null or t.transactionType=:type)
            and t.createdAt>=:from and t.createdAt<:until
            and ((:batchCode is null and :transactionCode is null and
                    (lower(v.sku) like lower(concat('%',:q,'%')) or lower(p.name) like lower(concat('%',:q,'%'))))
                 or (:batchCode is not null and t.batchId=:batchCode)
                 or (:transactionCode is not null and t.transactionId=:transactionCode))
            """)
    Page<InventoryTransaction> search(Long shop, Long variant, Long batch, StockTransactionType type,
                                      String q, Long batchCode, Long transactionCode,
                                      LocalDateTime from, LocalDateTime until, Pageable page);

}
