package VietFreshHub.payment.dto;

import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment obligation/attempt, not an independently collected revenue amount. */
public record PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
        String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt,
        String transactionReference, java.util.List<OrderReference> orders, String displayStatus, Long shopOrderId, BigDecimal shopAmount) {
    public record OrderReference(Long orderId, Long shopOrderId) {
        public String orderCode() { return VietFreshHub.common.BusinessCodes.format("VFH", shopOrderId); }
    }
    public PaymentHistoryRow {
        displayStatus = displayStatus == null ? switch (status) {
            case PENDING, PROCESSING -> "PENDING"; case PAID -> "PAID"; case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED"; case REFUNDED, PARTIALLY_REFUNDED -> "REFUND_PROCESSING";
        } : displayStatus;
        orders = orders == null ? java.util.List.of() : java.util.List.copyOf(orders);
        transactionReference = transactionReference == null || transactionReference.isBlank() ? null : transactionReference;
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
            String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt,
            String transactionReference) {
        this(paymentId, orderId, orderCode, methodName, methodCode, amount, status, createdAt, paidAt, transactionReference, java.util.List.of(), null, null, null);
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName, String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt, String transactionReference, String displayStatus) {
        this(paymentId,orderId,orderCode,methodName,methodCode,amount,status,createdAt,paidAt,transactionReference,java.util.List.of(),displayStatus,null,null);
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName, String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt, String transactionReference, String displayStatus, Long shopOrderId, BigDecimal shopAmount) {
        this(paymentId,orderId,orderCode,methodName,methodCode,amount,status,createdAt,paidAt,transactionReference,java.util.List.of(new OrderReference(orderId,shopOrderId)),displayStatus,shopOrderId,shopAmount);
    }
    public BigDecimal displayedAmount() { return shopOrderId == null ? amount : shopAmount; }
    public PaymentHistoryRow withOrders(java.util.List<OrderReference> references) {
        if (shopOrderId != null) return this;
        return new PaymentHistoryRow(paymentId, orderId, orderCode, methodName, methodCode, amount, status, createdAt, paidAt, transactionReference, references, displayStatus, shopOrderId, shopAmount);
    }
    public PaymentHistoryRow(Long paymentId, Long orderId, String orderCode, String methodName,
            String methodCode, BigDecimal amount, Payment.Status status, LocalDateTime createdAt, LocalDateTime paidAt) {
        this(paymentId,orderId,orderCode,methodName,methodCode,amount,status,createdAt,paidAt,null);
    }
    public boolean pendingCod() { return "PENDING".equals(displayStatus) && "COD".equals(methodCode); }
    public String statusLabel() { return PaymentHistoryDisplay.options().get(displayStatus); }
    // Retain the existing template accessor; both labels describe this payment record.
    public String transactionStatusLabel() { return statusLabel(); }
    public LocalDateTime createdAtLocal() { return OrderLabels.local(createdAt); }
    public LocalDateTime paidAtLocal() { return OrderLabels.local(paidAt); }
}
