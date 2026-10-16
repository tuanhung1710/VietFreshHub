package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.StockAllocation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAllocationRepository extends JpaRepository<StockAllocation, Long> {
    List<StockAllocation> findByOrderItemIdOrderByAllocationId(Long id);

    List<StockAllocation> findByBatchIdOrderByAllocationIdDesc(Long batchId);
}
