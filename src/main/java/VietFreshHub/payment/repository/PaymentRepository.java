package VietFreshHub.payment.repository;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    java.util.Optional<Payment> findByProviderAndTransactionReference(String provider, String reference);
    @org.springframework.data.jpa.repository.Query("select distinct p.order.orderId from Payment p where p.provider='VNPAY' and p.order.status=VietFreshHub.order.entity.Order.Status.PENDING and p.order.paymentStatus='UNPAID' and p.order.orderId>:afterId order by p.order.orderId")
    java.util.List<Long> findVnpayOrdersToExpire(@org.springframework.data.repository.query.Param("afterId") long afterId, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="paymentMethod")
    java.util.List<Payment> findByOrderOrderIdOrderByPaymentIdAsc(Long orderId);
}
