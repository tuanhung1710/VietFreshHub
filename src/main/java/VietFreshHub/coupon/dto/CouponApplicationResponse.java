package VietFreshHub.coupon.dto;

import java.math.BigDecimal;

public record CouponApplicationResponse(
        Long couponId,
        String code,
        Long promotionId,
        Long shopId,
        BigDecimal eligibleSubtotal,
        BigDecimal discountAmount,
        BigDecimal subtotalAfterDiscount
) {
}