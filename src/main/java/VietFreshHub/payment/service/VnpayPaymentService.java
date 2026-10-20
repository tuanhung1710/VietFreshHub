package VietFreshHub.payment.service;

import VietFreshHub.payment.dto.VnpayPaymentPage;
import VietFreshHub.payment.entity.Payment;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface VnpayPaymentService {
    boolean available();
    void prepare(Payment payment);
    boolean isOnlineOrder(Long customerId, Long orderId);
    VnpayPaymentPage page(Long customerId, Long orderId);
    URI start(Long customerId, Long orderId, String clientIp);
    Map<String, String> receiveIpn(Map<String, List<String>> parameters);
    boolean validReturn(Map<String, List<String>> parameters);
    void expire(Long orderId);
}
