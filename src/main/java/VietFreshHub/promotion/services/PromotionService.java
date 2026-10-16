package VietFreshHub.promotion.services;

import VietFreshHub.promotion.dto.PromotionForm;
import VietFreshHub.promotion.dto.PromotionResponse;

import java.util.List;

public interface PromotionService {

    List<PromotionResponse> getByShop(Long shopId);

    PromotionResponse getById(Long promotionId, Long shopId);

    PromotionResponse create(Long shopId, PromotionForm form);

    PromotionResponse update(
            Long promotionId,
            Long shopId,
            PromotionForm form
    );

    void activate(Long promotionId, Long shopId);

    void deactivate(Long promotionId, Long shopId);
}