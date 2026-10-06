package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.StockAllocation;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface StockAllocationRepository extends JpaRepository<StockAllocation,Long> {
    List<StockAllocation> findByOrderItemIdOrderByAllocationId(Long id);
    List<StockAllocation> findByBatchIdOrderByAllocationIdDesc(Long batchId);
}

