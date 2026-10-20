package VietFreshHub.catalog.service.impl;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
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
    private final CheckoutInventoryBatchRepository inventory;
    public boolean isVisible(CatalogProduct p) {
        if (p == null || !"ACTIVE".equals(p.getStatus()) || p.isModerationBlocked()
                || p.getShop() == null || !"ACTIVE".equals(p.getShop().getStatus())) return false;
        var activeIds = VietFreshHub.catalog.service.CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        return p.getCategories().stream().anyMatch(c -> activeIds.contains(c.getCategoryId()))
                && p.getVariants().stream().anyMatch(v -> "ACTIVE".equals(v.getStatus()) || "OUT_OF_STOCK".equals(v.getStatus()));
    }

    public boolean isPurchasable(CatalogVariant v) {
        return v != null && "ACTIVE".equals(v.getStatus()) && isVisible(v.getProduct())
                && "OPEN".equals(v.getProduct().getShop().getAvailabilityStatus());
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page) {
        return listProducts(page, "");
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page, String keyword) {
        if (page < 1 || page > 10000) throw new IllegalArgumentException("Trang không hợp lệ.");
        if (keyword == null || keyword.length() > 100) throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        var result = products.findVisibleCatalog(keyword.trim(), org.springframework.data.domain.PageRequest.of(page - 1, 12,
                org.springframework.data.domain.Sort.by("productId").descending()));
        var stocks = inventory.getAvailableStocks(result.getContent().stream().flatMap(p -> p.getVariants().stream())
                .map(CatalogVariant::getVariantId).distinct().toList());
        return result.map(p -> toResponse(p, null, stocks));
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
        var stocks = inventory.getAvailableStocks(p.getVariants().stream().map(CatalogVariant::getVariantId).toList());
        return toResponse(p, variantId, stocks);
    }
    private CatalogProductResponse toResponse(CatalogProduct p, Long selectedId, java.util.Map<Long, Integer> stocks) {
        var options = p.getVariants().stream().filter(v -> "ACTIVE".equals(v.getStatus()) || "OUT_OF_STOCK".equals(v.getStatus())).map(v -> {
            int available = stocks.getOrDefault(v.getVariantId(), 0);
            return CatalogProductResponse.VariantResponse.builder().variantId(v.getVariantId()).variantName(v.getVariantName())
                    .sku(v.getSku()).price(v.getPrice()).status(v.getStatus()).availableStock(available)
                    .purchasable("ACTIVE".equals(v.getStatus()) && available > 0 && "OPEN".equals(p.getShop().getAvailabilityStatus())).build();
        }).sorted(Comparator.comparing(CatalogProductResponse.VariantResponse::getPrice)).toList();
        var selected = selectedId == null ? options.stream().filter(CatalogProductResponse.VariantResponse::isPurchasable)
                .findFirst().orElse(options.isEmpty() ? null : options.getFirst())
                : options.stream().filter(v -> selectedId.equals(v.getVariantId())).findFirst().orElseThrow(this::notFound);
        return CatalogProductResponse.builder().productId(p.getProductId()).name(p.getName()).slug(p.getSlug())
                .description(p.getDescription()).imageUrl(selected == null ? p.getImageUrl() : p.getVariants().stream()
                        .filter(v -> v.getVariantId().equals(selected.getVariantId())).findFirst().map(CatalogVariant::getDisplayImageUrl).orElse(p.getImageUrl())).shopName(p.getShop().getShopName())
                .variants(options).selectedVariant(selected).build();
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Sản phẩm không còn được bán hoặc đường dẫn không hợp lệ."); }
}
