package VietFreshHub.payment.service;

import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

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
