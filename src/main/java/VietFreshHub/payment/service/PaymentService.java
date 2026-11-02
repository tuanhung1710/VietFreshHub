package VietFreshHub.payment.service;

import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getPaidOnlineOrderAmount(Long orderId) {
        Payment paidPayment = null;
        for (Payment payment : paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId)) {
            if (payment.getStatus() == Payment.Status.PROCESSING
                    || payment.getStatus() == Payment.Status.REFUNDED
                    || payment.getStatus() == Payment.Status.PARTIALLY_REFUNDED) {
                return Optional.empty();
            }
            if (payment.getStatus() == Payment.Status.PAID) {
                if (paidPayment != null) {
                    return Optional.empty();
                }
                paidPayment = payment;
            }
        }
        if (paidPayment == null || paidPayment.getPaymentMethod() == null || paidPayment.getPaidAt() == null
                || paidPayment.getAmount() == null || paidPayment.getAmount().signum() <= 0) {
            return Optional.empty();
        }
        String method = paidPayment.getPaymentMethod().getMethodCode();
        boolean online = "VNPAY".equals(method)
                || ("BANK_TRANSFER".equals(method) && "VNPAY".equals(paidPayment.getProvider()));
        return online ? Optional.of(paidPayment.getAmount()) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean isCodOrder(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        return !payments.isEmpty() && payments.stream()
                .allMatch(payment -> "COD".equals(payment.getPaymentMethod().getMethodCode()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reconcileCodPayment(Long orderId, BigDecimal amount, LocalDateTime paidAt, boolean noCollectibleOrders) {
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        if (payments.size() != 1 || !"COD".equals(payments.getFirst().getPaymentMethod().getMethodCode())) {
            return false;
        }
        Payment payment = paymentRepository.findByIdForUpdate(payments.getFirst().getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bản ghi thanh toán COD."));
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        if (!"COD".equals(payment.getPaymentMethod().getMethodCode()) || "VNPAY".equals(payment.getProvider())
                || payment.getPaidAt() != null
                || (payment.getStatus() != Payment.Status.PENDING
                && !(noCollectibleOrders && payment.getStatus() == Payment.Status.CANCELLED))) {
            return false;
        }
        if (noCollectibleOrders) {
            payment.setAmount(BigDecimal.ZERO);
            payment.setStatus(Payment.Status.CANCELLED);
            return false;
        }
        payment.setAmount(amount);
        if (paidAt == null) return false;
        payment.setStatus(Payment.Status.PAID);
        payment.setPaidAt(paidAt);
        return true;
    }

    @Transactional(readOnly = true)
    public String getInvoicePaymentMethodLabel(Long orderId) {
        Payment payment = paymentRepository.findTopByOrderOrderIdAndProviderOrderByPaymentIdDesc(orderId, "VNPAY")
                .or(() -> paymentRepository.findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(orderId, "VNPAY"))
                .orElseGet(() -> paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId).stream()
                        .findFirst().orElse(null));
        if (payment == null) {
            return "Chưa có thông tin";
        }
        return switch (payment.getPaymentMethod().getMethodCode()) {
            case "COD" -> "Thanh toán khi nhận hàng";
            case "BANK_TRANSFER", "VNPAY" -> "Thanh toán online";
            default -> "Chưa xác định";
        };
    }
}
