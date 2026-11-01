package VietFreshHub.catalog.service.impl;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.CatalogProductResponse.VariantResponse;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogBrowseRepository;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogCategoryPolicy;
import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.catalog.service.CatalogPricingService.Offer;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {
    private final CatalogProductRepository products;
    private final CatalogVariantRepository variants;
    private final CatalogPricingService pricing;
    private final CatalogBrowseRepository browse;

    @Override
    public boolean isVisible(CatalogProduct product) {
        if (!hasPublicMetadata(product)) return false;
        Map<Long, Offer> offers = offersFor(List.of(product));
        return product.getVariants().stream().anyMatch(variant -> hasOffer(variant, offers));
    }

    @Override
    public boolean isPurchasable(CatalogVariant variant) {
        if (variant == null || !"ACTIVE".equals(variant.getStatus()) || !hasPublicMetadata(variant.getProduct())) return false;
        return hasOffer(variant, pricing.offers(List.of(variant.getVariantId())));
    }

    private boolean hasPublicMetadata(CatalogProduct product) {
        if (product == null || !"ACTIVE".equals(product.getStatus()) || product.isModerationBlocked()
                || product.getShop() == null || !"ACTIVE".equals(product.getShop().getStatus())
                || !"OPEN".equals(product.getShop().getAvailabilityStatus())) return false;
        var activeCategories = CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        return product.getCategories().stream().anyMatch(category -> activeCategories.contains(category.getCategoryId()));
    }

    private boolean hasOffer(CatalogVariant variant, Map<Long, Offer> offers) {
        Offer offer = offers.get(variant.getVariantId());
        return "ACTIVE".equals(variant.getStatus()) && offer != null && offer.availableStock() > 0;
    }

    @Override
    public Page<CatalogProductResponse> listProducts(int page) {
        return listProducts(page, "");
    }

    @Override
    public Page<CatalogProductResponse> listProducts(int page, String keyword) {
        var filter = new CatalogBrowseFilter();
        filter.setPage(page);
        filter.setKeyword(keyword);
        return listProducts(filter);
    }

    @Override
    public List<CatalogCategory> categories() {
        var categories = products.findCatalogCategories();
        var active = CatalogCategoryPolicy.activeIds(categories);
        return categories.stream().filter(category -> active.contains(category.getCategoryId()))
                .sorted(Comparator.comparing(CatalogCategory::getName)).toList();
    }

    @Override
    public Page<CatalogProductResponse> listProducts(CatalogBrowseFilter filter) {
        if (filter == null || filter.getPage() < 1 || filter.getPage() > 10000) throw new IllegalArgumentException("Trang không hợp lệ.");
        if (filter.getKeyword() == null || filter.getKeyword().length() > 100) throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        if (!filter.isPriceRangeValid()) throw new IllegalArgumentException("Khoảng giá không hợp lệ.");
        var categoryIds = CatalogCategoryPolicy.matchingIds(products.findCatalogCategories(), filter.getCategoryId());
        var ids = browse.search(filter, categoryIds);
        var byId = products.findAllById(ids.getContent()).stream()
                .collect(Collectors.toMap(CatalogProduct::getProductId, Function.identity()));
        Map<Long, Offer> offers = offersFor(List.copyOf(byId.values()));
        return ids.map(id -> toResponse(byId.get(id), null, offers));
    }

    @Override
    public CatalogProductResponse getProduct(Long variantId, Long productId, String slug) {
        CatalogProduct product = findProduct(variantId, productId, slug);
        if (!hasPublicMetadata(product)) throw notFound();
        return toResponse(product, variantId, offersFor(List.of(product)));
    }

    private CatalogProduct findProduct(Long variantId, Long productId, String slug) {
        if (variantId != null) {
            var product = variants.findById(variantId).map(CatalogVariant::getProduct).orElseThrow(this::notFound);
            if (productId != null && !productId.equals(product.getProductId()) || slug != null && !slug.equals(product.getSlug())) throw notFound();
            return product;
        }
        if (productId != null) return products.findById(productId).orElseThrow(this::notFound);
        if (slug != null && !slug.isBlank()) {
            // Slugs are unique within a shop, so ambiguous links cannot select an arbitrary product.
            var matches = products.findAllBySlug(slug).stream().filter(this::isVisible).toList();
            if (matches.size() == 1) return matches.getFirst();
        }
        throw notFound();
    }

    private Map<Long, Offer> offersFor(List<CatalogProduct> catalogProducts) {
        return pricing.offers(catalogProducts.stream().flatMap(product -> product.getVariants().stream())
                .map(CatalogVariant::getVariantId).distinct().toList());
    }

    private CatalogProductResponse toResponse(CatalogProduct product, Long selectedId, Map<Long, Offer> offers) {
        var options = product.getVariants().stream().filter(variant -> hasOffer(variant, offers))
                .map(variant -> toVariant(variant, offers.get(variant.getVariantId())))
                .sorted(Comparator.comparing(VariantResponse::getPrice).thenComparing(VariantResponse::getVariantId)).toList();
        VariantResponse selected = options.stream().filter(option -> selectedId == null || selectedId.equals(option.getVariantId()))
                .findFirst().orElseThrow(this::notFound);
        String imageUrl = product.getVariants().stream().filter(variant -> selected.getVariantId().equals(variant.getVariantId()))
                .findFirst().map(CatalogVariant::getDisplayImageUrl).orElse(product.getImageUrl());
        return CatalogProductResponse.builder().productId(product.getProductId()).shopId(product.getShop().getShopId())
                .name(product.getName()).slug(product.getSlug()).description(product.getDescription()).imageUrl(imageUrl)
                .shopName(product.getShop().getShopName()).variants(options).selectedVariant(selected).build();
    }

    private VariantResponse toVariant(CatalogVariant variant, Offer offer) {
        return VariantResponse.builder().variantId(variant.getVariantId()).variantName(variant.getVariantName()).sku(variant.getSku())
                .price(offer.price()).basePrice(offer.basePrice()).status(variant.getStatus())
                .availableStock(offer.availableStock()).purchasable(true).build();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Sản phẩm không còn được bán hoặc đường dẫn không hợp lệ.");
    }
}
