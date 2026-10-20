package VietFreshHub.payment.service.impl;

import VietFreshHub.payment.dto.*;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.CustomerPaymentReadRepository;
import VietFreshHub.payment.service.CustomerPaymentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class CustomerPaymentHistoryServiceImpl implements CustomerPaymentHistoryService {
    private static final ZoneId CUSTOMER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CustomerPaymentReadRepository payments;

    @Override
    public Page<PaymentHistoryRow> history(Long customerId, PaymentHistoryFilter filter) {
        if (customerId == null || customerId < 1 || filter == null || filter.getPage() < 1 || filter.getPage() > 10000
                || filter.getKeyword() == null || filter.getKeyword().length() > 100
                || !filter.isDateRangeValid() || !filter.isDatabaseDateRangeValid()) {
            throw new IllegalArgumentException("Bộ lọc thanh toán không hợp lệ.");
        }
        String statusText = filter.getStatus() == null ? "" : filter.getStatus().trim();
        Payment.Status status;
        try { status = statusText.isEmpty() ? null : Payment.Status.valueOf(statusText); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Trạng thái thanh toán không hợp lệ."); }
        filter.setStatus(statusText);
        filter.setKeyword(filter.getKeyword().trim());

        LocalDateTime from = filter.getFromDate() == null ? null : utcStart(filter.getFromDate());
        if (from != null && from.getYear() < 1) from = LocalDateTime.of(1,1,1,0,0);
        LocalDate toDate = filter.getToDate();
        LocalDateTime until = toDate == null || toDate.equals(LocalDate.of(9999,12,31))
                ? null : utcStart(toDate.plusDays(1));
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("paymentId"));
        var page = payments.findHistory(customerId, status, filter.getKeyword(), from, until,
                PageRequest.of(filter.getPage()-1, 10, sort));
        if (page.getTotalElements() == 0) return Page.empty(PageRequest.of(0, 10, sort));
        if (page.getNumber() >= page.getTotalPages()) {
            return payments.findHistory(customerId, status, filter.getKeyword(), from, until,
                    PageRequest.of(page.getTotalPages()-1, 10, sort));
        }
        return page;
    }
    private LocalDateTime utcStart(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(CUSTOMER_ZONE).toInstant(), ZoneOffset.UTC);
    }
}
