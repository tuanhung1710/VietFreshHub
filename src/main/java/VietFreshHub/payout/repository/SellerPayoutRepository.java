package VietFreshHub.payout.repository;

import VietFreshHub.payout.entity.SellerPayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {

    boolean existsByShopOrderShopOrderId(Long shopOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SellerPayout p where p.shopOrder.shopOrderId = :shopOrderId")
    Optional<SellerPayout> findByShopOrderIdForUpdate(@Param("shopOrderId") Long shopOrderId);

    @EntityGraph(attributePaths = {"shop", "shopOrder", "shopOrder.order"})
    List<SellerPayout> findByStatusInOrderByEligibleAtAscPayoutIdAsc(Collection<SellerPayout.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SellerPayout p join fetch p.shop "
            + "where p.status in :statuses and p.eligibleAt <= :cutoff "
            + "and p.fundsConfirmedAt is not null and p.shopId in :shopIds "
            + "order by p.eligibleAt asc, p.payoutId asc")
    List<SellerPayout> lockEligibleForBatch(@Param("statuses") Collection<SellerPayout.Status> statuses,
                                            @Param("cutoff") LocalDateTime cutoff,
                                            @Param("shopIds") Collection<Long> shopIds);

    @EntityGraph(attributePaths = {"shop", "shopOrder", "shopOrder.order"})
    Optional<SellerPayout> findByPayoutId(Long payoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SellerPayout p where p.payoutId = :payoutId")
    Optional<SellerPayout> findByPayoutIdForUpdate(@Param("payoutId") Long payoutId);
}
