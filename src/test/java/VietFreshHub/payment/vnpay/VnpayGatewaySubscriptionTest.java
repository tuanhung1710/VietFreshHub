package VietFreshHub.payment.vnpay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewaySubscriptionTest {
    private static final String RETURN_URL = "https://shop.example/payment/vnpay/subscription/return";
    private static final String ORDER_INFO = "Subscription VietFresh Hub";

    @Test
    void subscriptionUrlUsesItsOwnReferenceAmountAndReturnUrl() {
        VnpayProperties properties = new VnpayProperties();
        properties.setHashSecret("test-secret");
        VnpayGateway gateway = new VnpayGateway(properties);

        String url = gateway.createPaymentUrl(new BigDecimal("125000.00"), "VFS41",
                LocalDateTime.of(2026, 1, 2, 3, 4), "127.0.0.1",
                RETURN_URL, ORDER_INFO);
        Map<String, String> fields = Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));

        assertEquals("12500000", fields.get("vnp_Amount"));
        assertEquals("VFS41", fields.get("vnp_TxnRef"));
        assertEquals("https://shop.example/payment/vnpay/subscription/return", fields.get("vnp_ReturnUrl"));
        assertTrue(gateway.verifySignature(fields));
        fields.put("vnp_Amount", "1");
        assertFalse(gateway.verifySignature(fields));
    }

    @Test
    void rejectsMissingNonPositiveAmount() {
        VnpayGateway gateway = readyGateway();

        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(null, "VFS41", null, "127.0.0.1", RETURN_URL, ORDER_INFO));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(BigDecimal.ZERO, "VFS41", null, "127.0.0.1", RETURN_URL, ORDER_INFO));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(new BigDecimal("-125000.00"), "VFS41", null, "127.0.0.1", RETURN_URL, ORDER_INFO));
    }

    @Test
    void rejectsMissingRequiredTextFields() {
        VnpayGateway gateway = readyGateway();
        BigDecimal amount = new BigDecimal("125000.00");

        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(amount, null, null, "127.0.0.1", RETURN_URL, ORDER_INFO));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(amount, " ", null, "127.0.0.1", RETURN_URL, ORDER_INFO));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(amount, "VFS41", null, "127.0.0.1", null, ORDER_INFO));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(amount, "VFS41", null, "127.0.0.1", RETURN_URL, null));
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(amount, "VFS41", null, "127.0.0.1", RETURN_URL, " "));
    }

    @Test
    void rejectsAmountThatCannotBeConvertedToVnpaySubunits() {
        VnpayGateway gateway = readyGateway();

        // Trên Long.MAX_VALUE nhân 100.
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(new BigDecimal("92233720368547758.08"), "VFS41", null,
                        "127.0.0.1", RETURN_URL, ORDER_INFO));
        // Hơn 2 chữ số thập phân -> không thể xác định subunit chính xác.
        assertThrows(IllegalArgumentException.class, () ->
                gateway.createPaymentUrl(new BigDecimal("125000.005"), "VFS41", null,
                        "127.0.0.1", RETURN_URL, ORDER_INFO));
    }

    private VnpayGateway readyGateway() {
        VnpayProperties properties = new VnpayProperties();
        properties.setHashSecret("test-secret");
        return new VnpayGateway(properties);
    }
}
