package VietFreshHub.payment.service.impl;

import VietFreshHub.payment.dto.PaymentHistoryFilter;
import VietFreshHub.payment.dto.PaymentHistoryRow;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.CustomerPaymentReadRepository;
import VietFreshHub.payment.service.CustomerPaymentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPaymentHistoryServiceImpl implements CustomerPaymentHistoryService {
    private static final ZoneId CUSTOMER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CustomerPaymentReadRepository payments;

    @Override
    public Page<PaymentHistoryRow> history(Long customerId, PaymentHistoryFilter filter) {
        validateFilter(customerId, filter);
        String statusText = "";
        if (filter.getStatus() != null) {
            statusText = filter.getStatus().trim();
        }
        Payment.Status status = parseStatus(statusText);
        filter.setStatus(statusText);
        filter.setKeyword(filter.getKeyword().trim());

        LocalDateTime from = startDate(filter.getFromDate());
        LocalDateTime until = endDateExclusive(filter.getToDate());
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("paymentId"));
        PageRequest requestedPage = PageRequest.of(filter.getPage() - 1, 10, sort);
        Page<PaymentHistoryRow> result = payments.findHistory(
                customerId, status, filter.getKeyword(), from, until, requestedPage);

        if (result.getTotalElements() == 0) {
            return Page.empty(PageRequest.of(0, 10, sort));
        }
        if (result.getNumber() >= result.getTotalPages()) {
            PageRequest lastPage = PageRequest.of(result.getTotalPages() - 1, 10, sort);
            return payments.findHistory(customerId, status, filter.getKeyword(), from, until, lastPage);
        }
        return result;
    }

    private void validateFilter(Long customerId, PaymentHistoryFilter filter) {
        if (customerId == null || customerId < 1 || filter == null || filter.getPage() < 1 || filter.getPage() > 10000
                || filter.getKeyword() == null || filter.getKeyword().length() > 100
                || !filter.isDateRangeValid() || !filter.isDatabaseDateRangeValid()) {
            throw new IllegalArgumentException("Bộ lọc thanh toán không hợp lệ.");
        }
    }

    private Payment.Status parseStatus(String statusText) {
        try {
            if (statusText.isEmpty()) {
                return null;
            }
            return Payment.Status.valueOf(statusText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Trạng thái thanh toán không hợp lệ.");
        }
    }

    private LocalDateTime startDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDateTime from = utcStart(date);
        // SQL Server không nhận năm 0 sau khi đổi nửa đêm Việt Nam sang UTC.
        if (from.getYear() < 1) {
            return LocalDateTime.of(1, 1, 1, 0, 0);
        }
        return from;
    }

    private LocalDateTime endDateExclusive(LocalDate date) {
        if (date == null || date.equals(LocalDate.of(9999, 12, 31))) {
            return null;
        }
        return utcStart(date.plusDays(1));
    }

    private LocalDateTime utcStart(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(CUSTOMER_ZONE).toInstant(), ZoneOffset.UTC);
    }
}
