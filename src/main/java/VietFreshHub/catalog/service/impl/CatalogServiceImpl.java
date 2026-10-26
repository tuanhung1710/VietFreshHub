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
        if (!isCatalogEligible(p)) return false;
        var stocks = inventory.getAvailableStocks(p.getVariants().stream()
                .filter(v -> "ACTIVE".equals(v.getStatus())).map(CatalogVariant::getVariantId).toList());
        return p.getVariants().stream().anyMatch(v -> "ACTIVE".equals(v.getStatus())
                && stocks.getOrDefault(v.getVariantId(), 0) > 0);
    }

    private boolean isCatalogEligible(CatalogProduct p) {
        if (p == null || !"ACTIVE".equals(p.getStatus()) || !"APPROVED".equals(p.getApprovalStatus())
                || p.getShop() == null || !"ACTIVE".equals(p.getShop().getStatus())) return false;
        var activeIds = VietFreshHub.catalog.service.CatalogCategoryPolicy.activeIds(products.findCatalogCategories());
        return p.getCategories().stream().anyMatch(c -> activeIds.contains(c.getCategoryId()))
                && p.getVariants().stream().anyMatch(v -> "ACTIVE".equals(v.getStatus()) || "OUT_OF_STOCK".equals(v.getStatus()));
    }

    public boolean isPurchasable(CatalogVariant v) {
        // Cart and checkout validate the requested quantity against stock separately.
        return v != null && "ACTIVE".equals(v.getStatus()) && isCatalogEligible(v.getProduct())
                && "OPEN".equals(v.getProduct().getShop().getAvailabilityStatus());
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page) {
        return listProducts(page, "");
    }
    public java.util.List<VietFreshHub.catalog.dto.CatalogCategoryResponse> categories() {
        var categories = products.findCatalogCategories();
        var active = VietFreshHub.catalog.service.CatalogCategoryPolicy.activeIds(categories);
        var parents = categories.stream().filter(c -> active.contains(c.getCategoryId()))
                .map(VietFreshHub.catalog.entity.CatalogCategory::getParentCategoryId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var options = new java.util.LinkedHashMap<String, VietFreshHub.catalog.dto.CatalogCategoryResponse>();
        String[][] groups = {
                {"trai-cay-viet-nam", "Trái cây tươi hằng ngày"},
                {"qua-tang-trai-cay", "Giỏ quà trái cây"},
                {"trai-cay-nhap-khau", "Trái cây nhập khẩu"},
                {"trai-cay-so-che", "Trái cây sơ chế"},
                {"trai-cay-theo-mua", "Trái cây theo mùa"},
                {"dac-san-vung-mien", "Đặc sản vùng miền"},
                {"trai-cay-huu-co", "Trái cây hữu cơ"},
                {"combo-gia-dinh", "Combo gia đình"},
                {"trai-cay-nuoc-ep-sinh-to", "Trái cây ép & sinh tố"},
                {"qua-bieu-le-tet", "Quà biếu lễ Tết"}
        };
        for (var group : groups) {
            var existing = categories.stream().filter(c -> group[0].equals(c.getSlug())).findFirst();
            // Missing groups remain available for future data; disabled categories stay hidden.
            if (existing.isEmpty() || active.contains(existing.get().getCategoryId())) {
                options.put(group[0], new VietFreshHub.catalog.dto.CatalogCategoryResponse(group[0], group[1]));
            }
        }
        categories.stream().filter(c -> active.contains(c.getCategoryId()) && !parents.contains(c.getCategoryId()))
                .sorted(Comparator.comparing(VietFreshHub.catalog.entity.CatalogCategory::getCategoryId))
                .forEach(c -> options.putIfAbsent(c.getSlug(),
                        new VietFreshHub.catalog.dto.CatalogCategoryResponse(c.getSlug(), c.getName())));
        return java.util.List.copyOf(options.values());
    }

    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page, String keyword) {
        return listProducts(page, keyword, "");
    }
    public org.springframework.data.domain.Page<CatalogProductResponse> listProducts(int page, String keyword, String category) {
        if (page < 1 || page > 10000) throw new IllegalArgumentException("Trang không hợp lệ.");
        if (keyword == null || keyword.length() > 100) throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        var pageable = org.springframework.data.domain.PageRequest.of(page - 1, 12,
                org.springframework.data.domain.Sort.by("productId").descending());
        if (category == null || category.length() > 200) throw new IllegalArgumentException("Danh mục không hợp lệ.");
        var result = category.isBlank() ? products.findVisibleCatalog(keyword.trim(), pageable)
                : products.findVisibleCatalog(keyword.trim(), category, pageable);
        if (result.getTotalPages() > 0 && pageable.getPageNumber() >= result.getTotalPages()) {
            var lastPage = pageable.withPage(result.getTotalPages() - 1);
            result = category.isBlank() ? products.findVisibleCatalog(keyword.trim(), lastPage)
                    : products.findVisibleCatalog(keyword.trim(), category, lastPage);
        }
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
            List<CatalogProduct> matches = products.findAllBySlug(slug).stream().filter(this::isCatalogEligible).toList();
            if (matches.size() != 1) throw notFound();
            p = matches.getFirst();
        } else throw notFound();
        // Trang chi tiết vẫn tồn tại khi hết hàng; danh sách mua sắm vẫn dùng isVisible().
        if (!isCatalogEligible(p)) throw notFound();
        var stocks = inventory.getAvailableStocks(p.getVariants().stream().map(CatalogVariant::getVariantId).toList());
        return toResponse(p, variantId, stocks);
    }
    private CatalogProductResponse toResponse(CatalogProduct p, Long selectedId, java.util.Map<Long, Integer> stocks) {
        var options = p.getVariants().stream().filter(v -> "ACTIVE".equals(v.getStatus())
                || "OUT_OF_STOCK".equals(v.getStatus())).map(v -> {
            int available = Math.max(0, stocks.getOrDefault(v.getVariantId(), 0));
            return CatalogProductResponse.VariantResponse.builder().variantId(v.getVariantId()).variantName(v.getVariantName())
                    .sku(v.getSku()).price(v.getPrice()).status(v.getStatus()).availableStock(available)
                    .imageUrl(v.getDisplayImageUrl()).availabilityMessage(availabilityMessage(p, v, available))
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
    private String availabilityMessage(CatalogProduct product, CatalogVariant variant, int availableStock) {
        if (!"OPEN".equals(product.getShop().getAvailabilityStatus())) {
            return "Cửa hàng hiện không nhận đơn.";
        }
        if ("OUT_OF_STOCK".equals(variant.getStatus()) || availableStock == 0) {
            return "Tạm hết hàng. Bạn có thể chọn phân loại khác hoặc quay lại sau.";
        }
        return "Còn " + availableStock + " sản phẩm có thể mua.";
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Sản phẩm không còn được bán hoặc đường dẫn không hợp lệ."); }
}
