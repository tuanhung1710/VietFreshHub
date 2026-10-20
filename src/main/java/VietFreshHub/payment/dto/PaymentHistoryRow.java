package VietFreshHub.payment.dto;

import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment record/attempt, not an independently collected revenue amount. */
public record PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
        String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt) {
    public String statusLabel() {
        return "COD".equals(methodCode) && status == Payment.Status.PENDING
                ? "Chờ thu tiền" : OrderLabels.paymentTransaction(status);
    }
    public LocalDateTime createdAtLocal() { return OrderLabels.local(createdAt); }
    public LocalDateTime paidAtLocal() { return OrderLabels.local(paidAt); }
}
