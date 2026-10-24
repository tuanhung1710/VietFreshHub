package VietFreshHub.payment.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentTimeoutScheduler {
    private final VnpayPaymentService payments;

    @Scheduled(fixedDelayString = "${app.vnpay.expiry-scan-ms:30000}")
    public void expirePendingPayments() {
        for (Long paymentId : payments.expiredPaymentIds()) {
            try {
                payments.expirePayment(paymentId);
            } catch (RuntimeException exception) {
                log.warn("Could not expire VNPAY paymentId={}, errorType={}", paymentId, exception.getClass().getSimpleName());
            }
        }
    }
}
