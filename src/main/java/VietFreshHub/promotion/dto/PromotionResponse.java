package VietFreshHub.promotion.dto;

import VietFreshHub.promotion.entity.DiscountType;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public record PromotionResponse(
        Long promotionId,
        Long shopId,
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minimumOrderValue,
        LocalDateTime startAt,
        LocalDateTime endAt,
        PromotionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getPromotionId(),
                promotion.getShopId(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMaxDiscount(),
                promotion.getMinimumOrderValue(),
                toDisplayTime(promotion.getStartAt()),
                toDisplayTime(promotion.getEndAt()),
                promotion.getStatus(),
                toDisplayTime(promotion.getCreatedAt()),
                toDisplayTime(promotion.getUpdatedAt())
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