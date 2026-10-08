package VietFreshHub.Product.service.impl;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.Auth.entity.SellerApplicationStatus;
import VietFreshHub.Auth.repository.SellerApplicationRepository;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.dto.CatalogProductResponse;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.repository.*;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SellerApplicationRepository applications;
    private final InventoryBatchRepository inventory;
    public boolean isVisible(Product p) {
        return p != null && "ACTIVE".equals(p.getStatus()) && "APPROVED".equals(p.getApprovalStatus())
                && p.getShop() != null && "ACTIVE".equals(p.getShop().getStatus()) && p.getShop().getApplicationId() != null
                && applications.findById(p.getShop().getApplicationId()).map(a -> a.getStatus() == SellerApplicationStatus.APPROVED).orElse(false);
    }
    public boolean isPurchasable(ProductVariant v) {
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
                .map(ProductVariant::getVariantId).distinct().toList());
        return result.map(p -> toResponse(p, null, stocks));
    }
    public CatalogProductResponse getProduct(Long variantId, Long productId, String slug) {
        Product p;
        if (variantId != null) {
            p = variants.findById(variantId).map(ProductVariant::getProduct).orElseThrow(this::notFound);
            if ((productId != null && !productId.equals(p.getProductId())) || (slug != null && !slug.equals(p.getSlug()))) throw notFound();
        } else if (productId != null) p = products.findById(productId).orElseThrow(this::notFound);
        else if (slug != null && !slug.isBlank()) {
            // Schema guarantees slug uniqueness within a shop only.
            List<Product> matches = products.findAllBySlug(slug).stream().filter(this::isVisible).toList();
            if (matches.size() != 1) throw notFound();
            p = matches.getFirst();
        } else throw notFound();
        if (!isVisible(p)) throw notFound();
        var stocks = inventory.getAvailableStocks(p.getVariants().stream().map(ProductVariant::getVariantId).toList());
        return toResponse(p, variantId, stocks);
    }
    private CatalogProductResponse toResponse(Product p, Long selectedId, java.util.Map<Long, Integer> stocks) {
        var options = p.getVariants().stream().filter(v -> !"DELETED".equals(v.getStatus())).map(v -> {
            int available = stocks.getOrDefault(v.getVariantId(), 0);
            return CatalogProductResponse.VariantResponse.builder().variantId(v.getVariantId()).variantName(v.getVariantName())
                    .sku(v.getSku()).price(v.getPrice()).status(v.getStatus()).availableStock(available)
                    .purchasable("ACTIVE".equals(v.getStatus()) && available > 0 && "OPEN".equals(p.getShop().getAvailabilityStatus())).build();
        }).sorted(Comparator.comparing(CatalogProductResponse.VariantResponse::getPrice)).toList();
        var selected = selectedId == null ? options.stream().filter(CatalogProductResponse.VariantResponse::isPurchasable)
                .findFirst().orElse(options.isEmpty() ? null : options.getFirst())
                : options.stream().filter(v -> selectedId.equals(v.getVariantId())).findFirst().orElseThrow(this::notFound);
        return CatalogProductResponse.builder().productId(p.getProductId()).name(p.getName()).slug(p.getSlug())
                .description(p.getDescription()).imageUrl(p.getImageUrl()).shopName(p.getShop().getShopName())
                .variants(options).selectedVariant(selected).build();
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Sản phẩm không còn được bán hoặc đường dẫn không hợp lệ."); }
}
