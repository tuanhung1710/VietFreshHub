package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface LoyaltyService {

    // Xem số dư điểm của khách hàng.
    LoyaltyBalanceResponse getBalance(Long customerId);

    // Xem lịch sử giao dịch điểm, có phân trang.
    Page<LoyaltyTransactionResponse> getTransactionHistory(
            Long customerId,
            Pageable pageable
    );

    // Trả số điểm vừa cộng; trả 0 nếu không cộng.
    int earnPoints(Long orderId);

    // Tính thử mức giảm, chưa trừ điểm.
// goodsAmountAfterCoupon phải do backend tính.
    LoyaltyRedemptionResponse previewRedemption(
            Long customerId,
            Integer pointsToUse,
            BigDecimal goodsAmountAfterCoupon
    );

    // Dùng điểm cho đơn đã tạo, sau bước áp dụng coupon.
// Cập nhật tiền đơn, trừ điểm và lưu giao dịch.
    LoyaltyRedemptionResponse redeemToOrder(
            Long customerId,
            Long orderId,
            Integer pointsToUse
    );
}