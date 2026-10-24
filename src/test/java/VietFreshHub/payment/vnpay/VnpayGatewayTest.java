package VietFreshHub.payment.vnpay;

import VietFreshHub.order.entity.Order;
import VietFreshHub.payment.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewayTest {
    @Test
    void createsSignedPaymentUrlAndRejectsTamperedParameters() {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TESTMERCHANT");
        properties.setHashSecret("unit-test-secret");
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:8080/payment/vnpay/return");
        properties.setExpiryMinutes(15);
        VnpayGateway gateway = new VnpayGateway(properties);

        Order order = new Order();
        order.setOrderCode("VF-TEST-001");
        Payment payment = new Payment();
        payment.setPaymentId(321L);
        payment.setTransactionReference("VF321");
        payment.setAmount(new BigDecimal("12345.00"));
        payment.setOrder(order);

        String url = gateway.createPaymentUrl(payment, "127.0.0.1");
        Map<String, String> fields = Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));

        assertEquals("1234500", fields.get("vnp_Amount"));
        assertEquals("VF321", fields.get("vnp_TxnRef"));
        assertEquals("TESTMERCHANT", fields.get("vnp_TmnCode"));
        assertTrue(gateway.verifySignature(fields));

        fields.put("vnp_Amount", "1");
        assertFalse(gateway.verifySignature(fields));
    }
}
