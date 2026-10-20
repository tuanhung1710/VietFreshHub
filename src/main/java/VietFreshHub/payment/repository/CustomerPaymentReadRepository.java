package VietFreshHub.payment.repository;

import VietFreshHub.payment.dto.PaymentHistoryRow;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

/** Read-only API; never select provider payloads or expose a save method. */
public interface CustomerPaymentReadRepository extends Repository<Payment, Long> {
    @Query("""
            select new VietFreshHub.payment.dto.PaymentHistoryRow(
                p.paymentId, p.order.orderId, p.order.orderCode, p.paymentMethod.name,
                p.paymentMethod.methodCode, p.amount, p.status, p.createdAt, p.paidAt)
            from Payment p
            where p.order.customer.userId = :customerId
              and (:status is null or p.status = :status)
              and (:keyword = '' or locate(lower(:keyword), lower(p.order.orderCode)) > 0)
              and (:fromDate is null or p.createdAt >= :fromDate)
              and (:untilDate is null or p.createdAt < :untilDate)
            """)
    Page<PaymentHistoryRow> findHistory(@Param("customerId") Long customerId,
            @Param("status") Payment.Status status, @Param("keyword") String keyword,
            @Param("fromDate") LocalDateTime fromDate, @Param("untilDate") LocalDateTime untilDate, Pageable pageable);
}
