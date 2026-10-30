package VietFreshHub.refund.service;

import VietFreshHub.delivery.service.DeliveryMonitoringService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.order.service.OrderRefundService;
import VietFreshHub.order.service.OrderRefundService.RefundShopOrder;
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
        Order order = orders.getForCustomer(orderId, authentication);
        RefundRequest submitted = requests.findByOrderOrderIdAndShopOrderIsNull(orderId).orElse(null);
        return new CustomerRefundState(order.getOrderId(), null,
                submitted == null ? null : submitted.getRefundAmount(), false,
                submitted == null ? null : OrderLabels.local(submitted.getSubmittedAt()),
                submitted == null ? "Vui lòng chọn đơn cửa hàng để xem thông tin hoàn tiền." : null);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public CustomerRefundState getCustomerShopState(Long shopOrderId, Authentication authentication) {
        return describeShop(orders.getShopForCustomer(shopOrderId, authentication));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public void submitShop(Long shopOrderId, Authentication authentication, @NotNull @Valid RefundInformationForm form) {
        RefundShopOrder refundOrder = orders.lockShopForCustomer(shopOrderId, authentication);
        CustomerRefundState state = describeShop(refundOrder);
        if (state.submittedAt() != null) return;
        if (!state.eligible()) throw new ResponseStatusException(HttpStatus.CONFLICT, state.unavailableReason());
        requests.saveAndFlush(new RefundRequest(refundOrder.shopOrder(), form.getBankName().strip(),
                form.getBankAccountNumber(), form.getAccountHolderName().strip(), state.refundAmount(),
                LocalDateTime.now(ZoneOffset.UTC).withNano(0)));
    }

    private CustomerRefundState describeShop(RefundShopOrder refundOrder) {
        var shopOrder = refundOrder.shopOrder();
        Order order = shopOrder.getOrder();
        RefundRequest submitted = requests.findByShopOrderShopOrderId(shopOrder.getShopOrderId()).orElse(null);
        if (submitted != null) {
            return new CustomerRefundState(order.getOrderId(), shopOrder.getShopOrderCode(), submitted.getRefundAmount(),
                    false, OrderLabels.local(submitted.getSubmittedAt()), null);
        }
        BigDecimal paidAmount = payments.getPaidOnlineOrderAmount(order.getOrderId()).orElse(null);
        BigDecimal refundAmount = shopOrder.getTotalAmount();
        String reason = null;
        if (requests.findByOrderOrderIdAndShopOrderIsNull(order.getOrderId()).isPresent()) {
            reason = "Lượt đặt hàng đã có yêu cầu hoàn tiền toàn bộ.";
        } else if (!"PAID".equals(shopOrder.getPaymentStatus()) || paidAmount == null
                || order.getGrandTotal() == null || paidAmount.compareTo(order.getGrandTotal()) != 0) {
            reason = "Không xác minh được khoản thanh toán online thành công của đơn cửa hàng.";
        } else if (refundAmount == null || refundAmount.signum() <= 0
                || requests.sumRefundAmount(order.getOrderId()).add(refundAmount).compareTo(paidAmount) > 0) {
            reason = "Số tiền hoàn của đơn cửa hàng không khớp khoản đã thanh toán.";
        } else if (!refundOrder.cancelledByCustomerBeforeConfirmation()
                && !deliveries.areAllFailedAndReturned(java.util.List.of(shopOrder.getShopOrderId()))) {
            reason = "Chỉ hỗ trợ hoàn tiền đơn cửa hàng đã hủy trước khi xác nhận,"
                    + " hoặc giao thất bại và cửa hàng đã xác nhận nhận lại hàng.";
        }
        return new CustomerRefundState(order.getOrderId(), shopOrder.getShopOrderCode(), refundAmount,
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
            String orderCode = request.getShopOrder() == null ? request.getOrder().getOrderCode()
                    : request.getShopOrder().getShopOrderCode();
            rows.add(new RefundPeriod.Row(request.getRefundRequestId(), orderCode,
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
