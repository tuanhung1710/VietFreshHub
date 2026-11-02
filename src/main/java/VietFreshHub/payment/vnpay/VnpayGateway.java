package VietFreshHub.payment.vnpay;

import VietFreshHub.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class VnpayGateway {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayProperties properties;

    public String createPaymentUrl(Payment payment, String remoteAddress) {
        if (!properties.isReady()) throw new IllegalStateException("VNPAY sandbox chưa được cấu hình.");
        if (payment.getPaymentId() == null || payment.getTransactionReference() == null
                || payment.getTransactionReference().isBlank() || payment.getAmount() == null) {
            throw new IllegalArgumentException("Thông tin lượt thanh toán VNPAY chưa đầy đủ.");
        }
        LocalDateTime createdAt = payment.getCreatedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC).withNano(0) : payment.getCreatedAt();
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_Amount", amountInSubunits(payment.getAmount()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", payment.getTransactionReference());
        fields.put("vnp_OrderInfo", "Thanh toán lượt đặt hàng VietFresh Hub");
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getReturnUrl());
        fields.put("vnp_IpAddr", normalizeIp(remoteAddress));
        fields.put("vnp_CreateDate", VNPAY_DATE.format(createdAt.atZone(ZoneOffset.UTC).withZoneSameInstant(VIETNAM)));
        fields.put("vnp_ExpireDate", VNPAY_DATE.format(createdAt.plusMinutes(properties.getExpiryMinutes())
                .atZone(ZoneOffset.UTC).withZoneSameInstant(VIETNAM)));
        String signature = sign(fields, properties.getHashSecret());
        fields.put("vnp_SecureHashType", "HmacSHA512");
        fields.put("vnp_SecureHash", signature);
        return properties.getPaymentUrl() + (properties.getPaymentUrl().contains("?") ? "&" : "?") + encode(fields);
    }

    public boolean verifySignature(Map<String, String> fields) {
        if (!properties.isReady() || fields == null) return false;
        String supplied = fields.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) return false;
        Map<String, String> data = new TreeMap<>(fields);
        data.remove("vnp_SecureHash");
        data.remove("vnp_SecureHashType");
        try {
            byte[] expected = HexFormat.of().parseHex(sign(data, properties.getHashSecret()));
            byte[] actual = HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String sign(Map<String, String> fields, String secret) {
        try {
            StringBuilder data = new StringBuilder();
            for (var entry : new TreeMap<>(fields).entrySet()) {
                if ("vnp_SecureHash".equals(entry.getKey()) || "vnp_SecureHashType".equals(entry.getKey())) continue;
                if (!data.isEmpty()) data.append('&');
                data.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            }
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(hmac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo checksum VNPAY.", exception);
        }
    }

    private String encode(Map<String, String> fields) {
        StringBuilder query = new StringBuilder();
        for (var entry : new TreeMap<>(fields).entrySet()) {
            if (!query.isEmpty()) query.append('&');
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return query.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String amountInSubunits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact() + "";
    }

    private String normalizeIp(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank() || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress)) return "127.0.0.1";
        return remoteAddress;
    }
}
