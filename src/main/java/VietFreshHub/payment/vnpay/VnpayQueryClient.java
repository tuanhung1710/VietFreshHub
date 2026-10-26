package VietFreshHub.payment.vnpay;

import VietFreshHub.payment.entity.Payment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VnpayQueryClient {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> RESPONSE_SIGNATURE_FIELDS = List.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
            "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
            "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount");
    private static final List<String> RESPONSE_FIELDS = List.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
            "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
            "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount", "vnp_SecureHash");

    public enum Outcome { PAID, FAILED, UNCERTAIN, SKIP }

    public record Result(Outcome outcome, Map<String, String> fields, String reason) {
        public Result {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }

    private final VnpayProperties properties;
    private final RestClient restClient;

    public VnpayQueryClient(VnpayProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Result query(Payment payment) {
        if (!properties.isQueryReady()) return uncertain(Map.of(), "QUERY_NOT_CONFIGURED");
        if (payment.getCreatedAt() == null || payment.getTransactionReference() == null
                || payment.getTransactionReference().isBlank()) {
            return uncertain(Map.of(), "PAYMENT_REFERENCE_MISSING");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String transactionDate = VNPAY_DATE.format(payment.getCreatedAt()
                .atZone(ZoneOffset.UTC).withZoneSameInstant(VIETNAM));
        String createDate = VNPAY_DATE.format(LocalDateTime.now(ZoneOffset.UTC)
                .atZone(ZoneOffset.UTC).withZoneSameInstant(VIETNAM));

        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", requestId);
        request.put("vnp_Version", "2.1.0");
        request.put("vnp_Command", "querydr");
        request.put("vnp_TmnCode", properties.getTmnCode());
        request.put("vnp_TxnRef", payment.getTransactionReference());
        request.put("vnp_OrderInfo", "Query payment " + payment.getTransactionReference());
        request.put("vnp_TransactionDate", transactionDate);
        request.put("vnp_CreateDate", createDate);
        request.put("vnp_IpAddr", properties.getQueryIpAddress());
        request.put("vnp_SecureHash", sign(String.join("|", requestId, "2.1.0", "querydr",
                properties.getTmnCode(), payment.getTransactionReference(), transactionDate, createDate,
                properties.getQueryIpAddress(), request.get("vnp_OrderInfo"))));

        try {
            JsonNode response = restClient.post()
                    .uri(properties.getQueryUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isObject()) return uncertain(Map.of(), "QUERY_RESPONSE_EMPTY");

            Map<String, String> fields = responseFields(response);
            if (!verifyResponse(fields)) return uncertain(Map.of(), "QUERY_RESPONSE_SIGNATURE_INVALID");
            if (!properties.getTmnCode().equals(fields.get("vnp_TmnCode"))
                    || !payment.getTransactionReference().equals(fields.get("vnp_TxnRef"))) {
                return uncertain(Map.of(), "QUERY_RESPONSE_REFERENCE_MISMATCH");
            }

            String responseCode = fields.get("vnp_ResponseCode");
            if ("91".equals(responseCode)) return new Result(Outcome.FAILED, fields, "TRANSACTION_NOT_FOUND");
            if (!"00".equals(responseCode)) return uncertain(fields, "QUERY_REQUEST_NOT_CONFIRMED");
            if (!amountMatches(payment.getAmount(), fields.get("vnp_Amount"))) {
                return uncertain(fields, "QUERY_AMOUNT_MISMATCH");
            }

            return switch (fields.get("vnp_TransactionStatus")) {
                case "00" -> new Result(Outcome.PAID, fields, null);
                case "02" -> new Result(Outcome.FAILED, fields, "TRANSACTION_FAILED");
                default -> uncertain(fields, "TRANSACTION_STATUS_UNCONFIRMED");
            };
        } catch (RuntimeException exception) {
            return uncertain(Map.of(), "QUERY_SERVICE_UNAVAILABLE");
        }
    }

    private Map<String, String> responseFields(JsonNode response) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String name : RESPONSE_FIELDS) {
            JsonNode value = response.get(name);
            if (value == null || value.isNull()) continue;
            String text = value.stringValue();
            fields.put(name, text == null ? value.toString() : text);
        }
        return fields;
    }

    private boolean verifyResponse(Map<String, String> fields) {
        String supplied = fields.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) return false;
        String data = RESPONSE_SIGNATURE_FIELDS.stream()
                .map(name -> fields.getOrDefault(name, ""))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        try {
            byte[] expected = HexFormat.of().parseHex(sign(data));
            byte[] actual = HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sign(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(hmac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ký yêu cầu QueryDR VNPAY.", exception);
        }
    }

    private boolean amountMatches(BigDecimal amount, String supplied) {
        try {
            return amount.movePointRight(2).longValueExact() == Long.parseLong(supplied);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Result uncertain(Map<String, String> fields, String reason) {
        return new Result(Outcome.UNCERTAIN, fields, reason);
    }
}
