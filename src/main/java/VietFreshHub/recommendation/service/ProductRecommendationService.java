package VietFreshHub.recommendation.service;

import VietFreshHub.recommendation.dto.ProductRecommendation;
import java.util.List;

public interface ProductRecommendationService {
    List<ProductRecommendation> relatedTo(Long productId);
}
