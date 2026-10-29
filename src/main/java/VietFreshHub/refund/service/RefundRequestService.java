package VietFreshHub.refund.service;

import VietFreshHub.delivery.service.DeliveryMonitoringService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.order.service.OrderRefundService;
import VietFreshHub.order.service.OrderRefundService.RefundOrder;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.refund.dto.CustomerRefundState;
import VietFreshHub.refund.dto.RefundInformationForm;
import VietFreshHub.refund.dto.RefundPeriod;
import VietFreshHub.refund.entity.RefundRequest;
import VietFreshHub.refund.repository.RefundRequestRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Validated
public class RefundRequestService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RefundRequestRepository requests;
    private final OrderRefundService orders;
    private final PaymentService payments;
    private final DeliveryMonitoringService deliveries;

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public CustomerRefundState getCustomerState(Long orderId, Authentication authentication) {
        RefundOrder order = orders.getForCustomer(orderId, authentication);
        return describe(order);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public void submit(Long orderId, Authentication authentication,
                       @NotNull @Valid RefundInformationForm form) {
        RefundOrder order = orders.lockForCustomer(orderId, authentication);
        CustomerRefundState state = describe(order);
        if (state.submittedAt() != null) {
            return;
        }
        if (!state.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, state.unavailableReason());
        }
        requests.saveAndFlush(new RefundRequest(order.order(), form.getBankName().strip(),
                form.getBankAccountNumber(), form.getAccountHolderName().strip(), state.refundAmount(),
                LocalDateTime.now(ZoneOffset.UTC).withNano(0)));
    }

    private CustomerRefundState describe(RefundOrder refundOrder) {
        Order order = refundOrder.order();
        RefundRequest submitted = requests.findByOrderOrderId(order.getOrderId()).orElse(null);
        if (submitted != null) {
            return new CustomerRefundState(order.getOrderId(), order.getOrderCode(), submitted.getRefundAmount(),
                    false, OrderLabels.local(submitted.getSubmittedAt()), null);
        }
        BigDecimal paidAmount = payments.getPaidOnlineOrderAmount(order.getOrderId()).orElse(null);
        String reason = null;
        if (!"PAID".equals(order.getPaymentStatus()) || paidAmount == null
                || order.getGrandTotal() == null || paidAmount.compareTo(order.getGrandTotal()) != 0) {
            reason = "Không xác minh được một khoản thanh toán online thành công khớp toàn bộ đơn hàng.";
        } else if (!refundOrder.cancelledByCustomerBeforeConfirmation()
                && !deliveries.areAllFailedAndReturned(refundOrder.shopOrderIds())) {
            reason = "Chỉ hỗ trợ hoàn toàn bộ đơn khi khách hủy trước lúc cửa hàng xác nhận,"
                    + " hoặc mọi phần giao hàng đều thất bại và cửa hàng đã xác nhận nhận lại hàng."
                    + " Đơn có kết quả giao hàng khác nhau nằm ngoài phạm vi này.";
        }
        return new CustomerRefundState(order.getOrderId(), order.getOrderCode(), paidAmount,
                reason == null, null, reason);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public RefundPeriod getAdminPeriod(String from, String to) {
        LocalDateTime now = LocalDateTime.now(VIETNAM).withNano(0);
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now;
        if (from != null || to != null) {
            if (from == null || to == null || from.isBlank() || to.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn cả thời gian bắt đầu và kết thúc.");
            }
            start = parseDateTime(from);
            end = parseDateTime(to);
        }
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian bắt đầu phải trước thời gian kết thúc.");
        }
        if (start.toLocalDate().isBefore(LocalDate.of(1, 1, 2))
                || end.toLocalDate().isAfter(now.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ngày hợp lệ, không nằm trong tương lai.");
        }
        LocalDateTime startUtc = start.atZone(VIETNAM).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endUtc = end.atZone(VIETNAM).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var rows = new ArrayList<RefundPeriod.Row>();
        BigDecimal total = BigDecimal.ZERO;
        for (RefundRequest request : requests
                .findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThanOrderBySubmittedAtAscRefundRequestIdAsc(startUtc, endUtc)) {
            if (request.getOrder() == null || request.getOrder().getOrderCode() == null
                    || request.getBankName() == null || request.getBankAccountNumber() == null
                    || request.getAccountHolderName() == null || request.getRefundAmount() == null
                    || request.getRefundAmount().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Kỳ có dữ liệu yêu cầu hoàn tiền không hợp lệ; cần kiểm tra dữ liệu trước khi xuất.");
            }
            rows.add(new RefundPeriod.Row(request.getRefundRequestId(), request.getOrder().getOrderCode(),
                    request.getBankName(), request.getBankAccountNumber(), request.getAccountHolderName(),
                    request.getRefundAmount(), OrderLabels.local(request.getSubmittedAt())));
            total = total.add(request.getRefundAmount());
        }
        return new RefundPeriod(start, end, now, rows, total);
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(value);
            if (parsed.getNano() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn thời gian chính xác đến giây.");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày giờ không hợp lệ. Vui lòng chọn đầy đủ ngày, giờ và phút.");
        }
    }
}
