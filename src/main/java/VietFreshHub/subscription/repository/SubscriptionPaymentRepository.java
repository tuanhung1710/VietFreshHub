package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.SubscriptionPayment;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByShop_ShopIdOrderByCreatedAtDescSubscriptionPaymentIdDesc(Long shopId);

    Optional<SubscriptionPayment> findBySubscriptionPaymentIdAndShop_ShopId(Long paymentId, Long shopId);

    boolean existsByShop_ShopIdAndStatus(Long shopId, SubscriptionPaymentStatus status);


}