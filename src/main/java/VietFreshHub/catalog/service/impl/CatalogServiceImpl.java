package VietFreshHub.catalog.service.impl;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {
    private final CatalogProductRepository products;
    private final CatalogVariantRepository variants;
    private final VietFreshHub.catalog.service.CatalogPricingService pricing;
    private final CatalogBrowseRepository browse;
    public boolean isVisible(CatalogProduct p) {
        if (p == null || !"ACTIVE".equals(p.getStatus()) || p.isModerationBlocked()
                || p.getShop() == null || !"ACTIVE".equals(p.getShop().getStatus())
                || !"OPEN".equals(p.getShop().getAvailabilityStatus())) return false;
        var activeIds = VietFreshHub.catalog.service.CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        var offers = pricing.offers(p.getVariants().stream().map(CatalogVariant::getVariantId).toList());
        return p.getCategories().stream().anyMatch(c -> activeIds.contains(c.getCategoryId()))
                && p.getVariants().stream().anyMatch(v -> "ACTIVE".equals(v.getStatus())
                && offers.containsKey(v.getVariantId()) && offers.get(v.getVariantId()).availableStock() > 0);
    }

    public boolean isPurchasable(CatalogVariant v) {
        if (v == null || !"ACTIVE".equals(v.getStatus()) || !isVisible(v.getProduct())) return false;
        var offer = pricing.offers(List.of(v.getVariantId())).get(v.getVariantId());
        return offer != null && offer.availableStock() > 0;
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page) {
        return listProducts(page, "");
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page, String keyword) {
        var filter = new VietFreshHub.catalog.dto.CatalogBrowseFilter();
        filter.setPage(page);
        filter.setKeyword(keyword);
        return listProducts(filter);
    }

    public List<CatalogCategory> categories() {
        var categories = products.findCatalogCategories();
        var active = VietFreshHub.catalog.service.CatalogCategoryPolicy.activeIds(categories);
        return categories.stream().filter(c -> active.contains(c.getCategoryId()))
                .sorted(Comparator.comparing(CatalogCategory::getName)).toList();
    }

    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(VietFreshHub.catalog.dto.CatalogBrowseFilter filter) {
        if (filter.getPage() < 1 || filter.getPage() > 10000) throw new IllegalArgumentException("Trang không hợp lệ.");
        if (filter.getKeyword() == null || filter.getKeyword().length() > 100) throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        var ids = browse.search(filter, VietFreshHub.catalog.service.CatalogCategoryPolicy.matchingIds(products.findCatalogCategories(), filter.getCategoryId()));
        var byId = products.findAllById(ids.getContent()).stream().collect(java.util.stream.Collectors.toMap(CatalogProduct::getProductId, java.util.function.Function.identity()));
        var offers = pricing.offers(byId.values().stream().flatMap(p -> p.getVariants().stream())
                .map(CatalogVariant::getVariantId).distinct().toList());
        return ids.map(id -> toResponse(byId.get(id), null, offers));
    }
    public CatalogProductResponse getProduct(Long variantId, Long productId, String slug) {
        CatalogProduct p;
        if (variantId != null) {
            p = variants.findById(variantId).map(CatalogVariant::getProduct).orElseThrow(this::notFound);
            if ((productId != null && !productId.equals(p.getProductId())) || (slug != null && !slug.equals(p.getSlug()))) throw notFound();
        } else if (productId != null) p = products.findById(productId).orElseThrow(this::notFound);
        else if (slug != null && !slug.isBlank()) {
            // Schema guarantees slug uniqueness within a shop only.
            List<CatalogProduct> matches = products.findAllBySlug(slug).stream().filter(this::isVisible).toList();
            if (matches.size() != 1) throw notFound();
            p = matches.getFirst();
        } else throw notFound();
        if (!isVisible(p)) throw notFound();
        var offers = pricing.offers(p.getVariants().stream().map(CatalogVariant::getVariantId).toList());
        return toResponse(p, variantId, offers);
    }
    private CatalogProductResponse toResponse(CatalogProduct p, Long selectedId,
            java.util.Map<Long, VietFreshHub.catalog.service.CatalogPricingService.Offer> offers) {
        var options = p.getVariants().stream().filter(v -> "ACTIVE".equals(v.getStatus())
                && offers.containsKey(v.getVariantId()) && offers.get(v.getVariantId()).availableStock() > 0).map(v -> {
            var offer = offers.get(v.getVariantId());
            int available = offer == null ? 0 : offer.availableStock();
            return CatalogProductResponse.VariantResponse.builder().variantId(v.getVariantId()).variantName(v.getVariantName())
                    .sku(v.getSku()).price(offer == null ? v.getPrice() : offer.price()).basePrice(v.getPrice()).status(v.getStatus()).availableStock(available)
                    .purchasable("ACTIVE".equals(v.getStatus()) && available > 0 && "OPEN".equals(p.getShop().getAvailabilityStatus())).build();
        }).sorted(Comparator.comparing(CatalogProductResponse.VariantResponse::getPrice)).toList();
        var selected = selectedId == null ? options.stream().findFirst().orElseThrow(this::notFound)
                : options.stream().filter(v -> selectedId.equals(v.getVariantId())).findFirst().orElseThrow(this::notFound);
        return CatalogProductResponse.builder().productId(p.getProductId()).shopId(p.getShop().getShopId()).name(p.getName()).slug(p.getSlug())
                .description(p.getDescription()).imageUrl(selected == null ? p.getImageUrl() : p.getVariants().stream()
                        .filter(v -> v.getVariantId().equals(selected.getVariantId())).findFirst().map(CatalogVariant::getDisplayImageUrl).orElse(p.getImageUrl())).shopName(p.getShop().getShopName())
                .variants(options).selectedVariant(selected).build();
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Sản phẩm không còn được bán hoặc đường dẫn không hợp lệ."); }
}
