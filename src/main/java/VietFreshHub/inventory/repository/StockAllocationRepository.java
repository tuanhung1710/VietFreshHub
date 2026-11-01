package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.StockAllocation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAllocationRepository extends JpaRepository<StockAllocation, Long> {
    /** Lấy các phân bổ của dòng đơn theo ID để xử lý giữ, giải phóng hoặc xuất bán. */
    List<StockAllocation> findByOrderItemIdOrderByAllocationId(Long id);

    /** Lấy các phân bổ của lô, mới nhất trước, để xem hàng đang liên quan đến đơn nào. */
    List<StockAllocation> findByBatchIdOrderByAllocationIdDesc(Long batchId);
}
