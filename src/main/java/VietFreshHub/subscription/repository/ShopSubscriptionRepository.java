package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.ShopSubscription;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository
        extends JpaRepository<ShopSubscription, Long> {

    @Query("""
            select s.subscriptionId
            from ShopSubscription s
            where s.status = :status
              and s.startAt <= :now
              and s.endAt > :now
              and s.endAt <= :deadline
            order by s.subscriptionId
            """)
    List<Long> findExpiringSubscriptionIds(
            @Param("status") SubscriptionStatus status,
            @Param("now") LocalDateTime now,
            @Param("deadline") LocalDateTime deadline
    );

    Optional<ShopSubscription> findByShop_ShopIdAndStatus(Long shopId, SubscriptionStatus status);

    List<ShopSubscription> findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(Long shopId);

    Optional<ShopSubscription> findBySubscriptionIdAndShop_ShopId(Long subscriptionId, Long shopId);
}
