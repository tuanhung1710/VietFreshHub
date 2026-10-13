package VietFreshHub.coupon.dto;

import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.promotion.entity.Promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public record CouponResponse(
        Long couponId,
        Long promotionId,
        String promotionName,
        String code,
        Integer usageLimit,
        Integer perUserLimit,
        BigDecimal minimumOrderValue,
        LocalDateTime startAt,
        LocalDateTime endAt,
        CouponStatus status
) {

    private static final ZoneId DISPLAY_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    public static CouponResponse from(Coupon coupon) {
        Promotion promotion = coupon.getPromotion();

        return new CouponResponse(
                coupon.getCouponId(),
                promotion.getPromotionId(),
                promotion.getName(),
                coupon.getCode(),
                coupon.getUsageLimit(),
                coupon.getPerUserLimit(),
                coupon.getMinimumOrderValue(),
                toDisplayTime(coupon.getStartAt()),
                toDisplayTime(coupon.getEndAt()),
                coupon.getStatus()
        );
    }

    private static LocalDateTime toDisplayTime(LocalDateTime utcTime) {
        if (utcTime == null) {
            return null;
        }

        return utcTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE)
                .toLocalDateTime();
    }
}