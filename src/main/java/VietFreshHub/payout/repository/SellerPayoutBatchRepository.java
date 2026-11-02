package VietFreshHub.payout.repository;

import VietFreshHub.payout.entity.SellerPayoutBatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerPayoutBatchRepository extends JpaRepository<SellerPayoutBatch, Long> {

    boolean existsByScheduledSlot(String scheduledSlot);

    List<SellerPayoutBatch> findAllByOrderByScheduledAtDesc(Pageable pageable);

    Optional<SellerPayoutBatch> findByBatchId(Long batchId);
}
