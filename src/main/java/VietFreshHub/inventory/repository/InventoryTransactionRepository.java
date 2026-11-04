package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.enums.StockTransactionType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    /** Kiểm tra phân loại đã có lịch sử biến động kho hay chưa. */
    boolean existsByVariantId(Long id);

    /** Lịch sử xử lý đơn của đúng lô và SKU, sắp thời gian rồi ID để ổn định thứ tự. */
    @Query(
            """
            select t from InventoryTransaction t
            where t.batchId=:batch and t.variantId=:variant
            and t.referenceType='ORDER_ITEM' and t.transactionType in :types
            order by t.createdAt asc, t.transactionId asc
            """)
    List<InventoryTransaction> findOrderProcessingHistory(
            Long batch, Long variant, List<StockTransactionType> types);
}
