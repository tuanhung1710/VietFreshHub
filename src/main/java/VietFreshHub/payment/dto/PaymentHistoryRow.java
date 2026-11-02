package VietFreshHub.payment.dto;

import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment obligation/attempt, not an independently collected revenue amount. */
public record PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
        String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt,
        String transactionReference, java.util.List<OrderReference> orders) {
    public record OrderReference(Long orderId, Long shopOrderId) {
        public String orderCode() { return VietFreshHub.common.BusinessCodes.format("VFH", shopOrderId); }
    }
    public PaymentHistoryRow {
        orders = orders == null ? java.util.List.of() : java.util.List.copyOf(orders);
        transactionReference = transactionReference == null || transactionReference.isBlank() ? null : transactionReference;
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
            String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt,
            String transactionReference) {
        this(paymentId, orderId, orderCode, methodName, methodCode, amount, status, createdAt, paidAt, transactionReference, java.util.List.of());
    }
    public PaymentHistoryRow withOrders(java.util.List<OrderReference> references) {
        return new PaymentHistoryRow(paymentId, orderId, orderCode, methodName, methodCode, amount, status, createdAt, paidAt, transactionReference, references);
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
