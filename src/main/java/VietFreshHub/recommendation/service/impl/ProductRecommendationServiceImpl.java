package VietFreshHub.recommendation.service.impl;

import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.entity.CatalogProduct;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRecommendationServiceImpl implements ProductRecommendationService {
    private static final int LIMIT = 6;
    private final ProductRecommendationRepository recommendations;
    private final CatalogProductRepository products;
    private final CatalogService catalog;

    @Override
    public List<ProductRecommendation> relatedTo(Long productId) {
        if (productId == null || productId < 1) {
            return List.of();
        }
        CatalogProduct source = recommendations.findByProductId(productId).orElse(null);
        if (source == null || !catalog.isVisible(source)) {
            return List.of();
        }
        Set<Long> activeCategoryIds = CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        if (activeCategoryIds.isEmpty()) {
            return List.of();
        }
        Set<Long> relatedCategoryIds = relatedCategoryIds(source, activeCategoryIds);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<CatalogProduct> candidates = recommendations.findCandidates(productId, source.getShop().getShopId(),
                activeCategoryIds, relatedCategoryIds, today, PageRequest.of(0, LIMIT));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = new ArrayList<>();
        for (CatalogProduct candidate : candidates) {
            productIds.add(candidate.getProductId());
        }
        Map<Long, ProductRecommendation> cheapestByProduct = cheapestVariants(productIds, today);
        List<ProductRecommendation> result = new ArrayList<>();
        for (CatalogProduct candidate : candidates) {
            ProductRecommendation card = cheapestByProduct.get(candidate.getProductId());
            if (card != null) {
                result.add(card);
            }
        }
        return List.copyOf(result);
    }

    private Set<Long> relatedCategoryIds(CatalogProduct source, Set<Long> activeCategoryIds) {
        Set<Long> relatedIds = new HashSet<>();
        for (CatalogCategory category : source.getCategories()) {
            if (activeCategoryIds.contains(category.getCategoryId())) {
                relatedIds.add(category.getCategoryId());
            }
        }
        // Giữ giá trị đánh dấu cũ để query không nhận danh sách IN rỗng.
        if (relatedIds.isEmpty()) {
            relatedIds.add(-1L);
        }
        return relatedIds;
    }

    private Map<Long, ProductRecommendation> cheapestVariants(List<Long> productIds, LocalDate today) {
        Map<Long, ProductRecommendation> cheapestByProduct = new HashMap<>();
        // Query đã sắp theo giá rồi variantId; dòng đầu mỗi sản phẩm là lựa chọn cũ cần giữ.
        for (ProductRecommendation variant : recommendations.findAvailableVariants(productIds, today)) {
            cheapestByProduct.putIfAbsent(variant.productId(), variant);
        }
        return cheapestByProduct;
    }
}
