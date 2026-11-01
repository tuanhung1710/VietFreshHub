package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryBatch;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    /** Lấy các lô của một phân loại theo hạn sử dụng và ID tăng dần. */
    List<InventoryBatch> findByVariantIdOrderByExpiryDateAscBatchIdAsc(Long variantId);

    /** Tải chung lô của nhiều phân loại theo hạn sử dụng và ID tăng dần. */
    List<InventoryBatch> findByVariantIdInOrderByExpiryDateAscBatchIdAsc(List<Long> ids);

    /** Khóa ghi lô theo ID trước khi cập nhật tồn, lượng giữ hoặc thông tin lô. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.batchId=:id")
    Optional<InventoryBatch> lockById(Long id);
}
