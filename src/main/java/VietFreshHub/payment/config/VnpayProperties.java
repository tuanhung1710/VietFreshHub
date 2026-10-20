package VietFreshHub.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration only. Never include the secret in logs or toString(). */
@Component
public class VnpayProperties {
    public final boolean enabled;
    public final String tmnCode;
    public final String paymentUrl;
    public final String returnUrl;
    private final String hashSecret;

    public VnpayProperties(
            @Value("${app.payment.vnpay.enabled:false}") boolean enabled,
            @Value("${app.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${app.payment.vnpay.hash-secret:}") String hashSecret,
            @Value("${app.payment.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String paymentUrl,
            @Value("${app.payment.vnpay.return-url:}") String returnUrl) {
        this.enabled = enabled;
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
    }

    public String hashSecret() { return hashSecret; }
}
