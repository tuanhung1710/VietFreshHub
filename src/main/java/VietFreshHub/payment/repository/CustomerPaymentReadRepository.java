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
    @Query("select new VietFreshHub.payment.dto.PaymentHistoryRow("
            + "p.paymentId,o.orderId,o.orderCode,p.paymentMethod.name,p.paymentMethod.methodCode,"
            + "p.amount,p.status,p.createdAt,p.paidAt,p.transactionReference," + VietFreshHub.payment.dto.PaymentHistoryDisplay.SHOP_CASE
            + ",s.shopOrderId,s.totalAmount) from Payment p join p.order o join ShopOrder s on s.order=o where o.customer.userId=:customerId "
            + "and (:status is null or (" + VietFreshHub.payment.dto.PaymentHistoryDisplay.SHOP_CASE + ")=:status) "
            + "and (:keyword='' or locate(lower(:keyword),lower(o.orderCode))>0 "
            + "or s.shopOrderId=:shopOrderId) "
            + "and (:fromDate is null or p.createdAt>=:fromDate) and (:untilDate is null or p.createdAt<:untilDate)")
    Page<PaymentHistoryRow> findHistoryWithOrderCode(@Param("customerId") Long customerId,
            @Param("status") String status, @Param("keyword") String keyword,
            @Param("fromDate") LocalDateTime fromDate, @Param("untilDate") LocalDateTime untilDate, @Param("shopOrderId") Long shopOrderId, Pageable pageable);

    default Page<PaymentHistoryRow> findHistory(Long customerId, String status, String keyword,
            LocalDateTime fromDate, LocalDateTime untilDate, Pageable pageable) {
        return findHistoryWithOrderCode(customerId, status, keyword, fromDate, untilDate,
                VietFreshHub.common.BusinessCodes.parse("VFH", keyword), pageable);
    }

    interface OrderReference { Long getOrderId(); Long getShopOrderId(); }

    @Query("select s.order.orderId as orderId, s.shopOrderId as shopOrderId from ShopOrder s "
            + "where s.order.customer.userId=:customerId and s.order.orderId in :orderIds order by s.shopOrderId")
    java.util.List<OrderReference> findOrderReferences(@Param("customerId") Long customerId,
            @Param("orderIds") java.util.List<Long> orderIds);
}
