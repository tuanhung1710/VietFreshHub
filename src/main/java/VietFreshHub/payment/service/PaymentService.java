package VietFreshHub.payment.service;

import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

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
