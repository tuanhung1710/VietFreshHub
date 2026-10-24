package VietFreshHub.payment.repository;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="paymentMethod")
    java.util.List<Payment> findByOrderOrderIdOrderByPaymentIdAsc(Long orderId);

    Optional<Payment> findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(Long orderId, String methodCode);

    @Query("select p from Payment p join fetch p.order where p.transactionReference=:reference")
    Optional<Payment> findByTransactionReferenceWithOrder(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId=:paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

    @Query("select p.paymentId from Payment p where p.paymentMethod.methodCode='VNPAY' and p.status=:status "
            + "and p.createdAt<=:cutoff order by p.paymentId")
    List<Long> findExpiredVnpayPaymentIds(@Param("cutoff") LocalDateTime cutoff, @Param("status") Payment.Status status);

    @Query("select case when count(p)>0 then true else false end from Payment p where p.order.orderId=:orderId and p.paymentMethod.methodCode='VNPAY' "
            + "and p.paymentId<>:paymentId and p.status in :statuses")
    boolean existsOtherVnpayPaymentWithStatus(@Param("orderId") Long orderId, @Param("paymentId") Long paymentId,
                                               @Param("statuses") List<Payment.Status> statuses);

    @Query("select p from Payment p where p.order.orderId=:orderId and p.paymentMethod.methodCode='VNPAY' "
            + "and p.paymentId<>:paymentId and p.status=:status order by p.paymentId")
    List<Payment> findOtherPendingVnpayPayments(@Param("orderId") Long orderId, @Param("paymentId") Long paymentId,
                                                @Param("status") Payment.Status status);
}
