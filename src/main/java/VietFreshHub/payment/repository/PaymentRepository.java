package VietFreshHub.payment.repository;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="paymentMethod")
    java.util.List<Payment> findByOrderOrderIdOrderByPaymentIdAsc(Long orderId);
}
