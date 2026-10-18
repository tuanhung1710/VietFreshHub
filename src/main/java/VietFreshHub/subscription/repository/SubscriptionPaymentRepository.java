package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByShop_ShopIdOrderByCreatedAtDescSubscriptionPaymentIdDesc(Long shopId);

    Optional<SubscriptionPayment> findBySubscriptionPaymentIdAndShop_ShopId(Long paymentId, Long shopId);



}