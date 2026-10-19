package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface LoyaltyService {

    // Xem số dư điểm của khách hàng.
    LoyaltyBalanceResponse getBalance(Long customerId);

    // Xem lịch sử giao dịch điểm, có phân trang.
    Page<LoyaltyTransactionResponse> getTransactionHistory(
            Long customerId,
            Pageable pageable
    );

}
