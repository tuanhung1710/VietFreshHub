package VietFreshHub.payment.vnpay;

import VietFreshHub.checkout.service.InventoryReservationService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.order.service.OrderNotificationService;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.entity.PaymentMethod;
import VietFreshHub.payment.entity.PaymentTransaction;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VnpayPaymentService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<Payment.Status> OPEN_STATUSES = List.of(Payment.Status.PENDING, Payment.Status.PROCESSING);

    private final VnpayProperties properties;
    private final VnpayGateway gateway;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentMethodRepository paymentMethods;
    private final PaymentTransactionRepository transactions;
    private final InventoryReservationService reservations;
    private final OrderInventoryReleaseService releases;
    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final VnpayQueryClient queryClient;
    private final EntityManager em;

    public record StartResult(String paymentUrl, String message) {
        public boolean started() { return paymentUrl != null; }
    }

    public record ReturnResult(Long orderId, boolean successful, String message) {}

    @Transactional
    public StartResult startInitialPayment(Long paymentId, Long customerId, String remoteAddress) {
        if (!properties.isReady()) return new StartResult(null, "Thanh toán VNPAY hiện chưa sẵn sàng. Vui lòng thử lại sau.");
        Payment snapshot = payments.findById(paymentId).orElseThrow(this::notFound);
        Long orderId = snapshot.getOrder().getOrderId();
        Order order = lockOrder(orderId);
        if (!Objects.equals(customerId, order.getCustomer().getUserId())) throw notFound();
        Payment payment = lockPayment(paymentId);
        requireVnpay(payment);
        if (order.getStatus() != Order.Status.PENDING || "PAID".equals(order.getPaymentStatus())) {
            return new StartResult(null, "Đơn hàng đã được xử lý. Vui lòng xem trạng thái trong chi tiết đơn.");
        }
        if (payment.getStatus() != Payment.Status.PENDING) {
            return new StartResult(null, "Lượt thanh toán này đã kết thúc. Bạn có thể tạo lượt thanh toán mới trong chi tiết đơn hàng.");
        }
        if (isExpired(payment, nowUtc())) {
            return new StartResult(null, "Lượt thanh toán đã hết 15 phút. Hệ thống đang xác minh trạng thái với VNPAY; vui lòng thử lại sau.");
        }
        return new StartResult(gateway.createPaymentUrl(payment, remoteAddress), null);
    }

    @Transactional(readOnly = true)
    public boolean canRetry(Long orderId, Long customerId) {
        if (!properties.isReady() || paymentMethods.findByMethodCodeAndStatus("BANK_TRANSFER", "ACTIVE").isEmpty()) return false;
        Order order = orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElse(null);
        if (order == null || order.getStatus() != Order.Status.PENDING || "PAID".equals(order.getPaymentStatus())) return false;
        Payment latest = latestVnpayPayment(orderId);
        if (latest == null || latest.getStatus() == Payment.Status.PROCESSING || latest.getStatus() == Payment.Status.PAID
                || latest.getStatus() == Payment.Status.CANCELLED) return false;
        if (payments.existsOtherVnpayPaymentWithStatus(orderId, latest.getPaymentId(), OPEN_STATUSES)) return false;
        return latest.getStatus() == Payment.Status.FAILED;
    }

    @Transactional
    public StartResult retryPayment(Long orderId, Long customerId, String remoteAddress) {
        if (!properties.isReady()) return new StartResult(null, "Thanh toán VNPAY hiện chưa sẵn sàng. Vui lòng thử lại sau.");
        Order order = orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElseThrow(this::notFound);
        order = lockOrder(orderId);
        if (!Objects.equals(customerId, order.getCustomer().getUserId())) throw notFound();
        if (order.getStatus() != Order.Status.PENDING || "PAID".equals(order.getPaymentStatus())) {
            return new StartResult(null, "Đơn hàng không còn ở trạng thái có thể thanh toán lại.");
        }
        Payment latest = Optional.ofNullable(latestVnpayPayment(orderId))
                .map(payment -> lockPayment(payment.getPaymentId())).orElse(null);
        if (latest == null) return new StartResult(null, "Không tìm thấy lượt thanh toán VNPAY của đơn hàng.");
        requireVnpay(latest);
        if (latest.getStatus() == Payment.Status.PENDING) {
            if (!isExpired(latest, nowUtc())) {
                return new StartResult(null, "Lượt thanh toán hiện tại vẫn còn hiệu lực. Vui lòng hoàn tất hoặc chờ đủ 15 phút.");
            }
            return new StartResult(null, "Lượt thanh toán đã hết hạn và đang được VNPAY xác minh. Vui lòng tải lại đơn hàng sau.");
        } else if (latest.getStatus() != Payment.Status.FAILED) {
            return new StartResult(null, "Lượt thanh toán đang được xử lý hoặc đã hoàn tất.");
        }
        if (payments.existsOtherVnpayPaymentWithStatus(orderId, latest.getPaymentId(), OPEN_STATUSES)) {
            return new StartResult(null, "Đơn hàng đang có một lượt thanh toán cần được xác nhận trước.");
        }
        PaymentMethod method = paymentMethods.findByMethodCodeAndStatus("BANK_TRANSFER", "ACTIVE").orElse(null);
        if (method == null) return new StartResult(null, "Thanh toán VNPAY hiện chưa khả dụng.");

        List<OrderItem> items = orderItems(orderId);
        List<InventoryReservationService.Reservation> lines = reservationsFor(items);
        InventoryReservationService.ReservationState state = reservations.reservationState(lines);
        if (state == InventoryReservationService.ReservationState.INCONSISTENT) {
            return new StartResult(null, "Kho của đơn hàng cần được kiểm tra trước khi tạo lượt thanh toán mới.");
        }
        if (state == InventoryReservationService.ReservationState.COMPLETE) {
            releases.release(releaseLines(items), customerId);
        }
        if (!reservations.canReserve(lines)) {
            updatePaymentStatus(order, "FAILED");
            return new StartResult(null, "Sản phẩm không còn đủ hàng để giữ cho lượt thanh toán mới. Vui lòng liên hệ hỗ trợ.");
        }
        reservations.reserve(lines, customerId);

        Payment next = new Payment();
        next.setOrder(order);
        next.setPaymentMethod(method);
        next.setAmount(order.getGrandTotal());
        next.setProvider("VNPAY");
        next.setStatus(Payment.Status.PENDING);
        em.persist(next);
        em.flush();
        next.setTransactionReference(transactionReference(next.getPaymentId()));
        updatePaymentStatus(order, "PENDING");
        em.flush();
        return new StartResult(gateway.createPaymentUrl(next, remoteAddress), null);
    }

    @Transactional
    public ReturnResult handleReturn(Map<String, String> fields) {
        if (!properties.isReady() || !gateway.verifySignature(fields)
                || !constantEquals(properties.getTmnCode(), fields.get("vnp_TmnCode"))) {
            return invalidReturn("Không xác minh được kết quả trả về từ VNPAY. Hãy kiểm tra trạng thái trong đơn hàng.");
        }
        String reference = fields.get("vnp_TxnRef");
        if (reference == null || reference.isBlank()) return invalidReturn("Không tìm thấy lượt thanh toán VNPAY tương ứng.");
        Payment lookup = payments.findByTransactionReferenceWithOrder(reference).orElse(null);
        if (lookup == null) return invalidReturn("Không tìm thấy lượt thanh toán VNPAY tương ứng.");
        Long orderId = lookup.getOrder().getOrderId();
        Order order = lockOrder(orderId);
        Payment payment = lockPayment(lookup.getPaymentId());
        if (!Objects.equals(reference, payment.getTransactionReference()) || !isVnpay(payment)) {
            return invalidReturn("Không tìm thấy lượt thanh toán VNPAY tương ứng.");
        }
        String returnedCurrency = fields.get("vnp_CurrCode");
        if (!amountMatches(payment.getAmount(), fields.get("vnp_Amount"))
                || (returnedCurrency != null && !"VND".equals(returnedCurrency))) {
            record(payment, "INVALID_AMOUNT", fields.get("vnp_TransactionNo"), "FAILED", fields);
            return new ReturnResult(orderId, false, "Thông tin giao dịch VNPAY không khớp với đơn hàng.");
        }
        boolean paid = "00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"));
        if (!paid) {
            if (payment.getStatus() == Payment.Status.FAILED || payment.getStatus() == Payment.Status.CANCELLED
                    || payment.getStatus() == Payment.Status.PAID) {
                return returnResult(orderId, false, "Lượt thanh toán này đã được xử lý. Hãy kiểm tra trạng thái đơn hàng.");
            }
            failLocked(order, payment, "PAYMENT_FAILED", fields);
            return returnResult(orderId, false, "Thanh toán VNPAY thất bại hoặc đã bị hủy. Bạn có thể thanh toán lại trong chi tiết đơn hàng.");
        }
        if (payment.getStatus() == Payment.Status.PAID) {
            return returnResult(orderId, true, "Thanh toán VNPAY đã được xác nhận.");
        }
        LocalDateTime paidAt;
        try {
            paidAt = parsePaidAt(fields.get("vnp_PayDate"));
        } catch (RuntimeException exception) {
            return returnResult(orderId, false, "Không xác minh được thời điểm thanh toán. Đơn hàng vẫn đang chờ xử lý.");
        }
        return applySuccessfulPayment(order, payment, paidAt, fields);
    }

    private ReturnResult applySuccessfulPayment(Order order, Payment payment, LocalDateTime paidAt,
                                                Map<String, String> fields) {
        Long orderId = order.getOrderId();
        if (payment.getStatus() == Payment.Status.PAID) {
            return returnResult(orderId, true, "Thanh toán VNPAY đã được xác nhận.");
        }
        if ("PAID".equals(order.getPaymentStatus())) {
            payment.setStatus(Payment.Status.PROCESSING);
            record(payment, "DUPLICATE_SUCCESS_REVIEW", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
            return returnResult(orderId, false, "Đơn hàng đã được thanh toán; lượt giao dịch này đang cần kiểm tra thêm.");
        }
        if (order.getStatus() != Order.Status.PENDING || order.getStatus() == Order.Status.CANCELLED) {
            payment.setStatus(Payment.Status.PROCESSING);
            record(payment, "SUCCESS_ORDER_REVIEW", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
            return returnResult(orderId, false, "VNPAY báo thanh toán thành công nhưng đơn hàng cần được kiểm tra thêm.");
        }
        List<OrderItem> items = orderItems(orderId);
        List<InventoryReservationService.Reservation> lines = reservationsFor(items);
        InventoryReservationService.ReservationState reservationState = reservations.reservationState(lines);
        if (reservationState == InventoryReservationService.ReservationState.NONE) {
            if (!reservations.canReserve(lines)) {
                payment.setStatus(Payment.Status.PROCESSING);
                updatePaymentStatus(order, "PENDING");
                record(payment, "LATE_SUCCESS_STOCK_UNAVAILABLE", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
                return returnResult(orderId, false, "VNPAY báo thanh toán thành công nhưng sản phẩm cần được kiểm tra tồn kho.");
            }
            reservations.reserve(lines, order.getCustomer().getUserId());
        } else if (reservationState == InventoryReservationService.ReservationState.INCONSISTENT) {
            payment.setStatus(Payment.Status.PROCESSING);
            updatePaymentStatus(order, "PENDING");
            record(payment, "SUCCESS_STOCK_REVIEW", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
            return returnResult(orderId, false, "VNPAY báo thanh toán thành công nhưng tồn kho cần được kiểm tra thêm.");
        }
        payment.setStatus(Payment.Status.PAID);
        payment.setPaidAt(paidAt);
        updatePaymentStatus(order, "PAID");
        record(payment, "PAYMENT_SUCCESS", fields.get("vnp_TransactionNo"), "PAID", fields);
        for (Payment other : payments.findOtherPendingVnpayPayments(orderId, payment.getPaymentId(), Payment.Status.PENDING)) {
            other.setStatus(Payment.Status.CANCELLED);
            record(other, "SUPERSEDED_BY_SUCCESS", fields.get("vnp_TransactionNo"), "CANCELLED", Map.of("event", "another VNPAY attempt paid the order"));
        }
        orderNotificationService.notifyPaidOrder(orderId);
        return returnResult(orderId, true, "Thanh toán VNPAY thành công.");
    }

    public VnpayQueryClient.Result queryExpiredPayment(Long paymentId) {
        Payment payment = payments.findByIdWithPaymentMethod(paymentId).orElse(null);
        if (payment == null || !isVnpay(payment) || payment.getStatus() != Payment.Status.PENDING
                || !isExpired(payment, nowUtc())) {
            return new VnpayQueryClient.Result(VnpayQueryClient.Outcome.SKIP, Map.of(), null);
        }
        return queryClient.query(payment);
    }

    @Transactional
    public boolean reconcileExpiredPayment(Long paymentId, VnpayQueryClient.Result queryResult) {
        if (queryResult == null || queryResult.outcome() == VnpayQueryClient.Outcome.SKIP) return false;
        Payment snapshot = payments.findById(paymentId).orElse(null);
        if (snapshot == null) return false;
        Order order = lockOrder(snapshot.getOrder().getOrderId());
        Payment payment = lockPayment(paymentId);
        if (!isVnpay(payment) || payment.getStatus() != Payment.Status.PENDING || !isExpired(payment, nowUtc())) return false;

        Map<String, String> fields = new LinkedHashMap<>(queryResult.fields());
        if (queryResult.outcome() == VnpayQueryClient.Outcome.PAID) {
            if (!Objects.equals(payment.getTransactionReference(), fields.get("vnp_TxnRef"))
                    || !amountMatches(payment.getAmount(), fields.get("vnp_Amount"))) {
                fields.put("queryReason", "QUERYDR_RESULT_MISMATCH");
                payment.setStatus(Payment.Status.PROCESSING);
                record(payment, "QUERYDR_UNCERTAIN", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
                return false;
            }
            LocalDateTime paidAt;
            try {
                paidAt = parsePaidAt(fields.get("vnp_PayDate"));
            } catch (RuntimeException exception) {
                fields.put("queryReason", "QUERYDR_PAY_DATE_INVALID");
                payment.setStatus(Payment.Status.PROCESSING);
                record(payment, "QUERYDR_UNCERTAIN", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
                return false;
            }
            return applySuccessfulPayment(order, payment, paidAt, fields).successful();
        }
        if (queryResult.outcome() == VnpayQueryClient.Outcome.FAILED) {
            failLocked(order, payment, "QUERYDR_FAILED", fields);
            return true;
        }

        payment.setStatus(Payment.Status.PROCESSING);
        fields.put("queryReason", queryResult.reason() == null ? "QUERYDR_UNCONFIRMED" : queryResult.reason());
        record(payment, "QUERYDR_UNCERTAIN", fields.get("vnp_TransactionNo"), "PROCESSING", fields);
        return false;
    }

    @Transactional(readOnly = true)
    public List<Long> expiredPaymentIds() {
        return payments.findExpiredVnpayPaymentIds(nowUtc().minusMinutes(properties.getExpiryMinutes()), Payment.Status.PENDING);
    }

    private void failLocked(Order order, Payment payment, String event, Map<String, String> fields) {
        payment.setStatus(Payment.Status.FAILED);
        boolean anotherOpenAttempt = payments.existsOtherVnpayPaymentWithStatus(order.getOrderId(), payment.getPaymentId(), OPEN_STATUSES);
        if (!anotherOpenAttempt && !"PAID".equals(order.getPaymentStatus()) && order.getStatus() == Order.Status.PENDING) {
            updatePaymentStatus(order, "FAILED");
            List<OrderItem> items = orderItems(order.getOrderId());
            releases.release(releaseLines(items), order.getCustomer().getUserId());
        }
        record(payment, event, fields.get("vnp_TransactionNo"), "FAILED", fields);
    }

    private void updatePaymentStatus(Order order, String paymentStatus) {
        order.setPaymentStatus(paymentStatus);
        orderService.updateShopPaymentStatusesForVnpay(order.getOrderId(), paymentStatus);
    }

    private void record(Payment payment, String event, String providerTransactionId, String status, Map<String, String> fields) {
        String transactionId = trimTo(providerTransactionId, 255);
        if (transactions.existsByPaymentPaymentIdAndEventTypeAndProviderTransactionId(
                payment.getPaymentId(), event, transactionId)) return;
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setEventType(event);
        transaction.setProviderTransactionId(transactionId);
        transaction.setAmount(payment.getAmount());
        transaction.setStatus(status);
        transaction.setProviderPayload(safePayload(fields));
        transactions.save(transaction);
    }

    private String safePayload(Map<String, String> fields) {
        if (fields == null) return "{}";
        Map<String, String> safe = new TreeMap<>();
        for (String key : List.of("vnp_TxnRef", "vnp_ResponseCode", "vnp_TransactionStatus", "vnp_TransactionNo",
                "vnp_Amount", "vnp_CurrCode", "vnp_BankCode", "vnp_PayDate", "queryReason")) {
            String value = fields.get(key);
            if (value != null) safe.put(key, trimTo(value, 255));
        }
        StringJoiner fieldsJson = new StringJoiner(",", "{", "}");
        safe.forEach((key, value) -> fieldsJson.add("\"" + jsonEscape(key) + "\":\"" + jsonEscape(value) + "\""));
        return fieldsJson.toString();
    }

    private List<OrderItem> orderItems(Long orderId) {
        return em.createQuery("select i from OrderItem i join fetch i.variant where i.shopOrder.order.orderId=:orderId order by i.orderItemId", OrderItem.class)
                .setParameter("orderId", orderId).getResultList();
    }

    private List<InventoryReservationService.Reservation> reservationsFor(List<OrderItem> items) {
        return items.stream().map(item -> new InventoryReservationService.Reservation(item.getOrderItemId(),
                item.getVariant().getVariantId(), item.getQuantity())).toList();
    }

    private List<OrderInventoryReleaseService.Line> releaseLines(List<OrderItem> items) {
        return items.stream().map(item -> new OrderInventoryReleaseService.Line(item.getOrderItemId(),
                item.getVariant().getVariantId(), item.getQuantity())).toList();
    }

    private Order lockOrder(Long orderId) {
        Order order = orders.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    private Payment lockPayment(Long paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(this::notFound);
        em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        return payment;
    }

    private void requireVnpay(Payment payment) {
        if (!isVnpay(payment)) throw notFound();
    }

    private boolean isVnpay(Payment payment) {
        return "VNPAY".equals(payment.getProvider())
                || (payment.getPaymentMethod() != null && "VNPAY".equals(payment.getPaymentMethod().getMethodCode()));
    }

    private Payment latestVnpayPayment(Long orderId) {
        return payments.findTopByOrderOrderIdAndProviderOrderByPaymentIdDesc(orderId, "VNPAY")
                .or(() -> payments.findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(orderId, "VNPAY"))
                .orElse(null);
    }

    private boolean isExpired(Payment payment, LocalDateTime now) {
        return payment.getCreatedAt() != null && !now.isBefore(payment.getCreatedAt().plusMinutes(properties.getExpiryMinutes()));
    }

    private boolean amountMatches(BigDecimal amount, String supplied) {
        try {
            return amount.movePointRight(2).longValueExact() == Long.parseLong(supplied);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private LocalDateTime parsePaidAt(String value) {
        if (value == null || value.isBlank()) return nowUtc();
        LocalDateTime vietnamLocal = LocalDateTime.parse(value, VNPAY_DATE);
        return LocalDateTime.ofInstant(vietnamLocal.atZone(VIETNAM).toInstant(), ZoneOffset.UTC);
    }

    private String transactionReference(Long paymentId) {
        return "VF" + paymentId;
    }

    private boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t");
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private ReturnResult invalidReturn(String message) {
        return new ReturnResult(null, false, message);
    }

    private ReturnResult returnResult(Long orderId, boolean successful, String message) {
        return new ReturnResult(orderId, successful, message);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy lượt thanh toán của đơn hàng.");
    }
}
