package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch,Long> {
    List<InventoryBatch> findByVariantIdOrderByExpiryDateAscBatchIdAsc(Long variantId);
    List<InventoryBatch> findByVariantIdInOrderByExpiryDateAscBatchIdAsc(List<Long> ids);
    boolean existsByVariantIdAndBatchCodeIgnoreCase(Long variantId, String batchCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.batchId=:id")
    Optional<InventoryBatch> lockById(Long id);
}

