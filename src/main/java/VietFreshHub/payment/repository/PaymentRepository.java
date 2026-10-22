package VietFreshHub.payment.repository;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    @org.springframework.data.jpa.repository.Query("select p from Payment p join fetch p.paymentMethod "
            + "where p.order.customer.userId=:customerId and p.order.orderId in :orderIds order by p.paymentId")
    java.util.List<Payment> findCustomerHistoryPayments(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("orderIds") java.util.List<Long> orderIds);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="paymentMethod")
    java.util.List<Payment> findByOrderOrderIdOrderByPaymentIdAsc(Long orderId);
}
