package VietFreshHub.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponUsageResponse(
        Long couponUsageId,
        Long couponId,
        String code,
        Long userId,
        String customerName,
        Long orderId,
        String orderCode,
        BigDecimal discountAmount,
        LocalDateTime usedAt
) {
}