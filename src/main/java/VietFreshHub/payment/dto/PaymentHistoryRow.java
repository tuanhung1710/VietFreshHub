package VietFreshHub.payment.dto;

import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment obligation/attempt, not an independently collected revenue amount. */
public record PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
        String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt,
        String transactionReference) {
    public PaymentHistoryRow {
        transactionReference = transactionReference == null || transactionReference.isBlank() ? null : transactionReference;
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
            String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt) {
        this(paymentId,orderId,orderCode,methodName,methodCode,amount,status,createdAt,paidAt,null);
    }
    public boolean pendingCod() { return status == Payment.Status.PENDING && "COD".equals(methodCode); }
    public String statusLabel() { return OrderLabels.paymentTransaction(status, methodCode); }
    // Retain the existing template accessor; both labels describe this payment record.
    public String transactionStatusLabel() { return statusLabel(); }
    public LocalDateTime createdAtLocal() { return OrderLabels.local(createdAt); }
    public LocalDateTime paidAtLocal() { return OrderLabels.local(paidAt); }
}
