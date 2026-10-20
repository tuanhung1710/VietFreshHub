package VietFreshHub.payment.service;

import VietFreshHub.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

@Component @RequiredArgsConstructor @Slf4j
public class VnpayExpiryJob {
    private final PaymentRepository payments;
    private final VnpayPaymentService service;
    private long cursor;

    @Scheduled(fixedDelayString="${app.payment.vnpay.expiry-scan-ms:60000}")
    public void expireUnpaidOrders() {
        // Every order runs in a separate transaction; one damaged audit cannot roll back the batch.
        var ids=payments.findVnpayOrdersToExpire(cursor,PageRequest.of(0,100));
        if(ids.isEmpty()) { cursor=0; return; }
        for(Long id:ids) {
            try { service.expire(id); }
            catch(RuntimeException ex) { log.warn("VNPAY expiry needs review, orderId={}, errorType={}",id,ex.getClass().getSimpleName()); }
        }
        cursor=ids.getLast();
    }
}
