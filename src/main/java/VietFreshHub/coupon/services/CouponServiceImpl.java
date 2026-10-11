package VietFreshHub.coupon.services;

import VietFreshHub.coupon.dto.CouponForm;
import VietFreshHub.coupon.dto.CouponResponse;
import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.repository.CouponRepository;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.repository.PromotionRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponServiceImpl implements CouponService {

    private static final ZoneId INPUT_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final CouponRepository couponRepository;
    private final PromotionRepository promotionRepository;
    private final Validator validator;

    @Override
    public List<CouponResponse> getByShop(Long shopId) {
        requireShopId(shopId);

        return couponRepository
                .findByPromotion_ShopIdOrderByCouponIdDesc(shopId)
                .stream()
                .map(CouponResponse::from)
                .toList();
    }

    @Override
    public CouponResponse getById(Long couponId, Long shopId) {
        Coupon coupon = findOwnedCoupon(couponId, shopId);

        return CouponResponse.from(coupon);
    }

    @Override
    @Transactional
    public CouponResponse create(Long shopId, CouponForm form) {
        requireShopId(shopId);
        validateForm(form);

        Promotion promotion = findOwnedPromotion(
                form.getPromotionId(),
                shopId
        );
        if (form.getMinimumOrderValue()
                .compareTo(promotion.getMinimumOrderValue()) < 0) {
            throw new IllegalArgumentException(
                    "Giá trị đơn tối thiểu của coupon không được thấp hơn "
                            + "mức tối thiểu của chương trình khuyến mãi."
            );
        }

        LocalDateTime startAt = toUtc(form.getStartAt());
        LocalDateTime endAt = toUtc(form.getEndAt());

        validatePromotionPeriod(promotion, startAt, endAt);

        String code = normalizeCode(form.getCode());

        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException(
                    "Mã coupon đã tồn tại"
            );
        }

        Coupon coupon = Coupon.builder()
                .promotion(promotion)
                .code(code)
                .usageLimit(form.getUsageLimit())
                .perUserLimit(form.getPerUserLimit())
                .minimumOrderValue(form.getMinimumOrderValue())
                .startAt(startAt)
                .endAt(endAt)
                .status(CouponStatus.INACTIVE)
                .build();

        Coupon saved = couponRepository.saveAndFlush(coupon);

        return CouponResponse.from(saved);
    }

    @Override
    @Transactional
    public CouponResponse update(
            Long couponId,
            Long shopId,
            CouponForm form
    ) {
        Coupon coupon = findOwnedCoupon(couponId, shopId);

        if (coupon.getStatus() != CouponStatus.INACTIVE) {
            throw new IllegalStateException(
                    "Chỉ được sửa coupon đang ở trạng thái tắt"
            );
        }

        validateForm(form);

        Promotion promotion = findOwnedPromotion(
                form.getPromotionId(),
                shopId
        );
        if (form.getMinimumOrderValue()
                .compareTo(promotion.getMinimumOrderValue()) < 0) {
            throw new IllegalArgumentException(
                    "Giá trị đơn tối thiểu của coupon không được thấp hơn "
                            + "mức tối thiểu của chương trình khuyến mãi."
            );
        }

        LocalDateTime startAt = toUtc(form.getStartAt());
        LocalDateTime endAt = toUtc(form.getEndAt());

        validatePromotionPeriod(promotion, startAt, endAt);

        String code = normalizeCode(form.getCode());

        boolean duplicated =
                couponRepository.existsByCodeIgnoreCaseAndCouponIdNot(
                        code,
                        couponId
                );

        if (duplicated) {
            throw new IllegalArgumentException(
                    "Mã coupon đã được sử dụng cho coupon khác"
            );
        }

        coupon.setPromotion(promotion);
        coupon.setCode(code);
        coupon.setUsageLimit(form.getUsageLimit());
        coupon.setPerUserLimit(form.getPerUserLimit());
        coupon.setMinimumOrderValue(form.getMinimumOrderValue());
        coupon.setStartAt(startAt);
        coupon.setEndAt(endAt);

        couponRepository.flush();

        return CouponResponse.from(coupon);
    }


    @Override
    @Transactional
    public void activate(Long couponId, Long shopId) {
        Coupon coupon = findOwnedCoupon(couponId, shopId);

        if (coupon.getStatus() == CouponStatus.DISABLED
                || coupon.getStatus() == CouponStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Không thể bật coupon đã vô hiệu hóa hoặc hết hạn"
            );
        }

        Promotion promotion = coupon.getPromotion();

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cần bật chương trình khuyến mãi trước khi bật coupon"
            );
        }

        validatePromotionPeriod(
                promotion,
                coupon.getStartAt(),
                coupon.getEndAt()
        );

        coupon.setStatus(CouponStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void deactivate(Long couponId, Long shopId) {
        Coupon coupon = findOwnedCoupon(couponId, shopId);

        if (coupon.getStatus() == CouponStatus.INACTIVE) {
            return;
        }

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Chỉ được tắt coupon đang ở trạng thái bật"
            );
        }

        coupon.setStatus(CouponStatus.INACTIVE);
    }

    @Override
    @Transactional
    public void disable(Long couponId, Long shopId) {
        Coupon coupon = findOwnedCoupon(couponId, shopId);

        coupon.setStatus(CouponStatus.DISABLED);
    }

    private Coupon findOwnedCoupon(Long couponId, Long shopId) {
        requireShopId(shopId);

        if (couponId == null || couponId <= 0) {
            throw new IllegalArgumentException(
                    "ID coupon không hợp lệ"
            );
        }

        return couponRepository
                .findByCouponIdAndPromotion_ShopId(couponId, shopId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy coupon thuộc shop này"
                ));
    }

    private Promotion findOwnedPromotion(
            Long promotionId,
            Long shopId
    ) {
        requireShopId(shopId);

        if (promotionId == null || promotionId <= 0) {
            throw new IllegalArgumentException(
                    "ID chương trình không hợp lệ"
            );
        }

        return promotionRepository
                .findByPromotionIdAndShopId(promotionId, shopId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy chương trình thuộc shop này"
                ));
    }

    private void requireShopId(Long shopId) {
        if (shopId == null || shopId <= 0) {
            throw new IllegalArgumentException(
                    "ID cửa hàng không hợp lệ"
            );
        }
    }

    private void validateForm(CouponForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu coupon không được để trống"
            );
        }

        var violations = validator.validate(form);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private void validatePromotionPeriod(
            Promotion promotion,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED
                || promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new IllegalArgumentException(
                    "Không thể dùng chương trình đã hủy hoặc hết hạn"
            );
        }

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu"
            );
        }

        if (!endAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời điểm hiện tại"
            );
        }

        if (startAt.isBefore(promotion.getStartAt())
                || endAt.isAfter(promotion.getEndAt())) {
            throw new IllegalArgumentException(
                    "Thời hạn coupon phải nằm trong thời hạn chương trình"
            );
        }
    }

    private String normalizeCode(String code) {
        return code.toUpperCase(Locale.ROOT);
    }

    private LocalDateTime toUtc(LocalDateTime localTime) {
        return localTime.atZone(INPUT_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}