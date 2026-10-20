package VietFreshHub.recommendation.dto;

import java.math.BigDecimal;

public record ProductRecommendation(Long productId, Long variantId, String productName,
        String variantName, String shopName, String imageUrl, BigDecimal price) {}
