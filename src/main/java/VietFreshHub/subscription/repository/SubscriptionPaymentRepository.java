package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.SubscriptionPayment;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByShop_ShopIdOrderByCreatedAtDescSubscriptionPaymentIdDesc(Long shopId);

    Optional<SubscriptionPayment> findBySubscriptionPaymentIdAndShop_ShopId(Long paymentId, Long shopId);

    boolean existsByShop_ShopIdAndStatus(Long shopId, SubscriptionPaymentStatus status);

    List<SubscriptionPayment> findByStatusOrderByCreatedAtAscSubscriptionPaymentIdAsc(SubscriptionPaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM SubscriptionPayment p
            WHERE p.subscriptionPaymentId = :paymentId
            """)
    Optional<SubscriptionPayment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}