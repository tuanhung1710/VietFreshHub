package VietFreshHub.payment.service;

import VietFreshHub.payment.dto.PaymentHistoryFilter;
import VietFreshHub.payment.dto.PaymentHistoryRow;
import org.springframework.data.domain.Page;

public interface CustomerPaymentHistoryService {
    Page<PaymentHistoryRow> history(Long customerId, PaymentHistoryFilter filter);
}
