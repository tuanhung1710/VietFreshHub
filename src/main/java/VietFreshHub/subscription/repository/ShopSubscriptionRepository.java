package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.ShopSubscription;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository
        extends JpaRepository<ShopSubscription, Long> {

    Optional<ShopSubscription> findByShop_ShopIdAndStatus(Long shopId, SubscriptionStatus status);

    List<ShopSubscription> findByShop_ShopIdOrderByCreatedAtDescSubscriptionIdDesc(Long shopId);

    Optional<ShopSubscription> findBySubscriptionIdAndShop_ShopId(Long subscriptionId, Long shopId);
}