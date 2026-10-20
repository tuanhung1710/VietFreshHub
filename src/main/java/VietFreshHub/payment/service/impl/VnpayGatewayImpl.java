package VietFreshHub.payment.service.impl;

import VietFreshHub.payment.config.VnpayProperties;
import VietFreshHub.payment.dto.VnpayPaymentRequest;
import VietFreshHub.payment.service.VnpayGateway;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class VnpayGatewayImpl implements VnpayGateway {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final VnpayProperties properties;

    public VnpayGatewayImpl(VnpayProperties properties) { this.properties = properties; }

    @Override
    public URI createPaymentUrl(VnpayPaymentRequest request) {
        if (!properties.enabled) throw new IllegalStateException("VNPAY is disabled");
        requireConfigured();
        if (request == null || request.reference() == null
                || !request.reference().matches("[A-Za-z0-9]{1,100}")
                || request.amount() == null || request.amount().signum() <= 0
                || request.clientIp() == null || !request.clientIp().matches("[0-9a-fA-F:.]{7,45}")
                || request.createdAt() == null || request.expiresAt() == null
                || !request.expiresAt().isAfter(request.createdAt())
                || DATE.format(request.createdAt()).equals(DATE.format(request.expiresAt()))) {
            throw new IllegalArgumentException("Invalid server-side VNPAY payment request");
        }
        String amount = request.amount().movePointRight(2).toBigIntegerExact().toString();
        if (amount.length() > 12) throw new IllegalArgumentException("VNPAY amount exceeds limit");

        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.tmnCode);
        fields.put("vnp_Amount", amount);
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", request.reference());
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + request.reference());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.returnUrl);
        fields.put("vnp_IpAddr", request.clientIp());
        fields.put("vnp_CreateDate", DATE.format(request.createdAt()));
        fields.put("vnp_ExpireDate", DATE.format(request.expiresAt()));
        String query = canonicalQuery(fields);
        return URI.create(properties.paymentUrl + "?" + query + "&vnp_SecureHash=" + sign(query));
    }

    @Override
    public boolean hasValidSignature(Map<String, List<String>> parameters) {
        requireConfigured();
        if (parameters == null) return false;
        Map<String, String> fields = new TreeMap<>();
        String signature = null;
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (key == null) return false;
            if (!key.startsWith("vnp_")) continue;
            List<String> values = entry.getValue();
            if (values == null || values.size() != 1 || values.getFirst() == null) return false;
            String value = values.getFirst();
            if (key.equals("vnp_SecureHash")) signature = value;
            else if (!key.equals("vnp_SecureHashType") && !value.isEmpty()) fields.put(key, value);
        }
        if (signature == null || !signature.matches("[a-fA-F0-9]{128}") || fields.isEmpty()) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(signature),
                HexFormat.of().parseHex(sign(canonicalQuery(fields))));
    }

    private void requireConfigured() {
        if (properties.tmnCode == null || !properties.tmnCode.matches("[A-Za-z0-9]{8}")
                || properties.hashSecret() == null || properties.hashSecret().isBlank()) {
            throw new IllegalStateException("VNPAY merchant configuration is missing");
        }
        requireHttpUrl(properties.paymentUrl, true);
        requireHttpUrl(properties.returnUrl, false);
    }

    private void requireHttpUrl(String value, boolean paymentEndpoint) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null || !("https".equals(uri.getScheme())
                || (!paymentEndpoint && "http".equals(uri.getScheme())))) {
            throw new IllegalStateException("Invalid VNPAY endpoint configuration");
        }
    }

    private String canonicalQuery(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate VNPAY signature", exception);
        }
    }
}
