package VietFreshHub.recommendation.service.impl;

import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.catalog.service.CatalogCategoryPolicy;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.recommendation.dto.ProductRecommendation;
import VietFreshHub.recommendation.repository.ProductRecommendationRepository;
import VietFreshHub.recommendation.service.ProductRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class ProductRecommendationServiceImpl implements ProductRecommendationService {
    private static final int LIMIT = 6;
    private final ProductRecommendationRepository recommendations;
    private final CatalogProductRepository products;
    private final CatalogService catalog;

    @Override
    public List<ProductRecommendation> relatedTo(Long productId) {
        if (productId == null || productId < 1) return List.of();
        var source = recommendations.findByProductId(productId).orElse(null);
        if (source == null || !catalog.isVisible(source)) return List.of();
        Set<Long> activeIds = CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        if (activeIds.isEmpty()) return List.of();
        Set<Long> relatedIds = new HashSet<>();
        for (var category : source.getCategories()) {
            if (activeIds.contains(category.getCategoryId())) relatedIds.add(category.getCategoryId());
        }
        // Avoid an empty IN clause; real category IDs are positive identities.
        if (relatedIds.isEmpty()) relatedIds.add(-1L);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var candidates = recommendations.findCandidates(productId, source.getShop().getShopId(), activeIds,
                relatedIds, today, PageRequest.of(0, LIMIT));
        if (candidates.isEmpty()) return List.of();

        var ids = candidates.stream().map(p -> p.getProductId()).toList();
        Map<Long, ProductRecommendation> cheapest = new HashMap<>();
        for (var variant : recommendations.findAvailableVariants(ids, today)) {
            cheapest.putIfAbsent(variant.productId(), variant);
        }
        List<ProductRecommendation> result = new ArrayList<>();
        for (var candidate : candidates) {
            var card = cheapest.get(candidate.getProductId());
            if (card != null) result.add(card);
        }
        return List.copyOf(result);
    }
}
