package VietFreshHub.payment.repository;

import VietFreshHub.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findFirstByPaymentPaymentIdAndEventTypeOrderByTransactionIdAsc(Long paymentId, String eventType);
    boolean existsByEventTypeAndProviderTransactionId(String eventType, String providerTransactionId);
}
