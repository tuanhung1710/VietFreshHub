package VietFreshHub.payout.repository;

import VietFreshHub.payout.entity.SellerPayoutBatchItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SellerPayoutBatchItemRepository extends JpaRepository<SellerPayoutBatchItem, Long> {

    @Query("select i from SellerPayoutBatchItem i join fetch i.payout p "
            + "join fetch p.shopOrder so join fetch so.order "
            + "where i.batch.batchId = :batchId order by i.shopNameSnapshot, i.batchItemId")
    List<SellerPayoutBatchItem> findForBatch(@Param("batchId") Long batchId);

    @Query("select i from SellerPayoutBatchItem i where i.batch.batchId = :batchId and i.shopId = :shopId")
    List<SellerPayoutBatchItem> findByBatchAndShop(@Param("batchId") Long batchId, @Param("shopId") Long shopId);

    long countByBatchBatchId(Long batchId);
}
