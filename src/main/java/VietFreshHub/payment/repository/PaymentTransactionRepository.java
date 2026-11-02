package VietFreshHub.payment.repository;

import VietFreshHub.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    boolean existsByPaymentPaymentIdAndEventTypeAndProviderTransactionId(
            Long paymentId, String eventType, String providerTransactionId);
}
