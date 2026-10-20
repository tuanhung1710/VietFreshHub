package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface LoyaltyService {

    // Xem số dư tiền thưởng của khách hàng.
    LoyaltyBalanceResponse getBalance(Long customerId);

    // Xem lịch sử giao dịch tiền thưởng, có phân trang.
    Page<LoyaltyTransactionResponse> getTransactionHistory(
            Long customerId,
            Pageable pageable
    );

    // Trả số tiền thưởng vừa cộng; trả 0 nếu không cộng.
    BigDecimal earnReward(Long orderId);

    // Tính thử mức giảm, chưa trừ tiền thưởng.
// goodsAmountAfterCoupon phải do backend tính.
    LoyaltyRedemptionResponse previewRedemption(
            Long customerId,
            BigDecimal rewardAmountToUse,
            BigDecimal goodsAmountAfterCoupon
    );

    // Dùng tiền thưởng cho đơn đã tạo, sau bước áp dụng coupon.
// Cập nhật tiền đơn, trừ tiền thưởng và lưu giao dịch.
    LoyaltyRedemptionResponse redeemToOrder(
            Long customerId,
            Long orderId,
            BigDecimal rewardAmountToUse
    );
}