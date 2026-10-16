package VietFreshHub.promotion.services;

import VietFreshHub.promotion.dto.PromotionForm;
import VietFreshHub.promotion.dto.PromotionResponse;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private static final ZoneId INPUT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PromotionRepository promotionRepository;
    private final Validator validator;

    @Override
    public List<PromotionResponse> getByShop(Long shopId) {
        requireShopId(shopId);

        return promotionRepository
                .findByShopIdOrderByCreatedAtDesc(shopId)
                .stream()
                .map(PromotionResponse::from)
                .toList();
    }

    @Override
    public PromotionResponse getById(Long promotionId, Long shopId) {
        Promotion promotion = findOwnedPromotion(promotionId, shopId);

        return PromotionResponse.from(promotion);
    }

    @Override
    @Transactional
    public PromotionResponse create(Long shopId, PromotionForm form) {
        requireShopId(shopId);
        validateForm(form);

        Promotion promotion = Promotion.builder()
                .shopId(shopId)
                .name(form.getName().strip())
                .description(form.getDescription())
                .discountType(form.getDiscountType())
                .discountValue(form.getDiscountValue())
                .maxDiscount(form.getMaxDiscount())
                .minimumOrderValue(form.getMinimumOrderValue())
                .startAt(toUtc(form.getStartAt()))
                .endAt(toUtc(form.getEndAt()))
                .status(PromotionStatus.INACTIVE)
                .build();

        Promotion saved = promotionRepository.saveAndFlush(promotion);

        return PromotionResponse.from(saved);
    }

    @Override
    @Transactional
    public PromotionResponse update(
            Long promotionId,
            Long shopId,
            PromotionForm form
    ) {
        Promotion promotion = findOwnedPromotion(promotionId, shopId);

        if (promotion.getStatus() != PromotionStatus.INACTIVE) {
            throw new IllegalStateException(
                    "Chỉ được sửa chương trình đang ở trạng thái INACTIVE"
            );
        }

        validateForm(form);

        promotion.setName(form.getName().strip());
        promotion.setDescription(form.getDescription());
        promotion.setDiscountType(form.getDiscountType());
        promotion.setDiscountValue(form.getDiscountValue());
        promotion.setMaxDiscount(form.getMaxDiscount());
        promotion.setMinimumOrderValue(form.getMinimumOrderValue());
        promotion.setStartAt(toUtc(form.getStartAt()));
        promotion.setEndAt(toUtc(form.getEndAt()));

        promotionRepository.flush();

        return PromotionResponse.from(promotion);
    }

    @Override
    @Transactional
    public void activate(Long promotionId, Long shopId) {
        Promotion promotion = findOwnedPromotion(promotionId, shopId);

        if (promotion.getStatus() == PromotionStatus.CANCELLED
                || promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Không thể kích hoạt chương trình đã hủy hoặc hết hạn"
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (!promotion.getEndAt().isAfter(now)) {
            throw new IllegalStateException(
                    "Thời gian kết thúc đã qua, không thể kích hoạt"
            );
        }

        promotion.setStatus(PromotionStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void deactivate(Long promotionId, Long shopId) {
        Promotion promotion = findOwnedPromotion(promotionId, shopId);

        if (promotion.getStatus() == PromotionStatus.INACTIVE) {
            return;
        }

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Chỉ được tắt chương trình đang ACTIVE"
            );
        }

        promotion.setStatus(PromotionStatus.INACTIVE);
    }

    private Promotion findOwnedPromotion(Long promotionId, Long shopId) {
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

    private void validateForm(PromotionForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu chương trình không được để trống"
            );
        }

        var violations = validator.validate(form);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private void requireShopId(Long shopId) {
        if (shopId == null || shopId <= 0) {
            throw new IllegalArgumentException(
                    "Chưa xác định được shop hợp lệ"
            );
        }
    }

    private LocalDateTime toUtc(LocalDateTime localTime) {
        return localTime.atZone(INPUT_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}