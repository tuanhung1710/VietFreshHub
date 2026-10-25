package VietFreshHub.payment.vnpay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayProperties {
    private boolean enabled = true;
    private String tmnCode = "6CBHNFW9";
    private String hashSecret = "OAFFBSOXOTCJOFEDUYHDYZSPOEGSCOBC";
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String queryUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String queryIpAddress = "127.0.0.1";
    private String returnUrl = "http://localhost:8080/payment/vnpay/return";
    private int expiryMinutes = 15;

    public boolean isReady() {
        return enabled && hasText(tmnCode) && hasText(hashSecret) && hasText(paymentUrl) && hasText(returnUrl)
                && expiryMinutes > 0;
    }

    public boolean isQueryReady() {
        return isReady() && hasText(queryUrl) && hasText(queryIpAddress);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    public String getQueryUrl() { return queryUrl; }
    public void setQueryUrl(String queryUrl) { this.queryUrl = queryUrl; }
    public String getQueryIpAddress() { return queryIpAddress; }
    public void setQueryIpAddress(String queryIpAddress) { this.queryIpAddress = queryIpAddress; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public int getExpiryMinutes() { return expiryMinutes; }
    public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }
}
