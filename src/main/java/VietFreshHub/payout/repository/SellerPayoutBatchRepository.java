package VietFreshHub.payout.repository;

import VietFreshHub.payout.entity.SellerPayoutBatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SellerPayoutBatchRepository extends JpaRepository<SellerPayoutBatch, Long> {

    boolean existsByScheduledSlot(String scheduledSlot);

    List<SellerPayoutBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select batch from SellerPayoutBatch batch "
            + "where (:fromUtc is null or batch.createdAt >= :fromUtc) "
            + "and (:toUtcExclusive is null or batch.createdAt < :toUtcExclusive) "
            + "order by batch.createdAt desc")
    List<SellerPayoutBatch> findCreatedAtRange(@Param("fromUtc") LocalDateTime fromUtc,
                                               @Param("toUtcExclusive") LocalDateTime toUtcExclusive);

    Optional<SellerPayoutBatch> findByBatchId(Long batchId);
}
