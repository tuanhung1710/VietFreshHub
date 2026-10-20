package VietFreshHub.payment;

import VietFreshHub.payment.config.VnpayProperties;
import VietFreshHub.payment.dto.VnpayPaymentRequest;
import VietFreshHub.payment.service.impl.VnpayGatewayImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewayTest {
    private final VnpayGatewayImpl gateway = gateway(true, "test-secret");

    private VnpayGatewayImpl gateway(boolean enabled, String secret) {
        return new VnpayGatewayImpl(new VnpayProperties(enabled, "DEMOV210", secret,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://example.com/payments/vnpay/return"));
    }

    private VnpayPaymentRequest request(String amount) {
        return new VnpayPaymentRequest("TEST123", new BigDecimal(amount), "127.0.0.1",
                Instant.parse("2026-10-20T03:00:00Z"), Instant.parse("2026-10-20T03:10:00Z"));
    }

    private Map<String, List<String>> parameters() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String field : gateway.createPaymentUrl(request("10000.25")).getRawQuery().split("&")) {
            String[] pair = field.split("=", 2);
            result.put(pair[0], new ArrayList<>(List.of(URLDecoder.decode(pair[1], StandardCharsets.UTF_8))));
        }
        return result;
    }

    @Test void createsExactAmountAndVietnamTime() {
        var fields = parameters();
        assertEquals(List.of("1000025"), fields.get("vnp_Amount"));
        assertEquals(List.of("20261020100000"), fields.get("vnp_CreateDate"));
        assertEquals(List.of("20261020101000"), fields.get("vnp_ExpireDate"));
        assertEquals(List.of("https://example.com/payments/vnpay/return"), fields.get("vnp_ReturnUrl"));
        assertFalse(fields.containsKey("vnp_BankCode"));
    }

    @Test void verifiesEncodedFieldsAndIgnoresNonVnpParameters() {
        var fields = parameters();
        fields.put("tracking", List.of("untrusted"));
        assertTrue(gateway.hasValidSignature(fields));
    }

    @Test void rejectsTamperingAndWrongSecret() {
        var fields = parameters();
        assertFalse(gateway(true, "another-secret").hasValidSignature(fields));
        fields.put("vnp_Amount", List.of("1"));
        assertFalse(gateway.hasValidSignature(fields));
    }

    @Test void rejectsDuplicateParameters() {
        var fields = parameters();
        fields.get("vnp_Amount").add("1000025");
        assertFalse(gateway.hasValidSignature(fields));
    }

    @Test void verifiesIndependentPythonHmacVector() {
        // Generated independently with Python hmac/sha512 over sorted URL-encoded fields.
        assertTrue(gateway.hasValidSignature(Map.of(
                "vnp_Amount", List.of("1000000"),
                "vnp_OrderInfo", List.of("Thanh toan don hang TEST123"),
                "vnp_ResponseCode", List.of("00"),
                "vnp_TmnCode", List.of("DEMOV210"),
                "vnp_TransactionStatus", List.of("00"),
                "vnp_TxnRef", List.of("TEST123"),
                "vnp_SecureHash", List.of("cfb0e033702fefe609bfbea06aa1a1d35c39d7b93377cdbc0264cf6da675c34045f4e0c963d13ddde14bd5fbe002a7f302667d6b294f5bbe282090561af97c6f"))));
    }

    @Test void rejectsMissingOrMalformedSignature() {
        var fields = parameters();
        fields.remove("vnp_SecureHash");
        assertFalse(gateway.hasValidSignature(fields));
        fields.put("vnp_SecureHash", List.of("not-hex"));
        assertFalse(gateway.hasValidSignature(fields));
        assertFalse(gateway.hasValidSignature(Map.of()));
    }

    @Test void disabledGatewayCannotBeUsed() {
        assertThrows(IllegalStateException.class, () -> gateway(false, "").createPaymentUrl(request("10000")));
        assertTrue(gateway(false, "test-secret").hasValidSignature(parameters()));
    }

    @Test void rejectsMissingMerchantSecret() {
        assertThrows(IllegalStateException.class, () -> gateway(true, "").createPaymentUrl(request("10000")));
    }

    @Test void rejectsInvalidAmountsWithoutRounding() {
        for (String amount : List.of("0", "-1", "10000000000")) {
            assertThrows(IllegalArgumentException.class, () -> gateway.createPaymentUrl(request(amount)));
        }
        assertThrows(ArithmeticException.class, () -> gateway.createPaymentUrl(request("1.001")));
    }

    @Test void rejectsInvalidReferenceAndExpiry() {
        var valid = request("10000");
        assertThrows(IllegalArgumentException.class, () -> gateway.createPaymentUrl(new VnpayPaymentRequest(
                "bad&ref", valid.amount(), valid.clientIp(), valid.createdAt(), valid.expiresAt())));
        assertThrows(IllegalArgumentException.class, () -> gateway.createPaymentUrl(new VnpayPaymentRequest(
                valid.reference(), valid.amount(), valid.clientIp(), valid.createdAt(), valid.createdAt())));
    }
}
