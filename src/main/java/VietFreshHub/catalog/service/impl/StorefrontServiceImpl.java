package VietFreshHub.catalog.service.impl;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.dto.StorefrontProductDetail;
import VietFreshHub.catalog.dto.StorefrontShopHomeResponse;
import VietFreshHub.catalog.dto.StorefrontShopOption;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.repository.CatalogDetailRepository;
import VietFreshHub.catalog.repository.StorefrontShopRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.common.PageSizes;
import VietFreshHub.common.Pagination;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.service.ShopBrowseService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorefrontServiceImpl implements StorefrontService {

    private final CatalogService catalogService;
    private final StorefrontShopRepository storefrontShopRepository;
    private final CatalogDetailRepository catalogDetailRepository;
    private final ShopBrowseService shopBrowseService;

    @Override
    public Page<CatalogProductResponse> searchProducts(
            StorefrontCatalogFilter filters, int page, int size) {
        return findProducts(prepareFilters(filters, page, size));
    }

    @Override
    public Page<CatalogProductResponse> searchShopProducts(
            Long shopId, StorefrontCatalogFilter filters, int page, int size) {
        filters.setShopIds(List.of());
        CatalogBrowseFilter query = prepareFilters(filters, page, size);
        query.setShopId(shopId);
        query.setShopIds(List.of());
        return findProducts(query);
    }

    /** Trang cũ không còn tồn tại sau khi đổi bộ lọc thì quay về trang đầu như danh sách của người bán. */
    private Page<CatalogProductResponse> findProducts(CatalogBrowseFilter query) {
        Page<CatalogProductResponse> rows = catalogService.listProducts(query);
        if (query.getPage() > Math.max(1, rows.getTotalPages())) {
            query.setPage(1);
            return catalogService.listProducts(query);
        }
        return rows;
    }

    /** Chuẩn hóa điều kiện của form trước khi chuyển xuống phần truy vấn catalog. */
    private CatalogBrowseFilter prepareFilters(
            StorefrontCatalogFilter filters, int page, int size) {
        filters.setQ(filters.getQ() == null ? "" : filters.getQ().strip());
        filters.setSort(normalizeSort(filters.getSort()));
        CatalogBrowseFilter query = new CatalogBrowseFilter();
        query.setPage(Math.min(Pagination.MAX_PAGE, Math.max(1, page)));
        query.setPageSize(PageSizes.normalize(size));
        query.setKeyword(filters.getQ());
        query.setCategoryId(filters.getCategory());
        query.setShopIds(filters.getShopIds());
        query.setPlaces(filters.getPlaces());
        query.setMinRating(filters.getMinRating());
        query.setMinPrice(filters.getMinPrice());
        query.setMaxPrice(filters.getMaxPrice());
        query.setSort(filters.getSort());
        return query;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "popular";
        }
        return switch (sort.strip()) {
            case "latest", "newest" -> "latest";
            case "sales", "best_selling" -> "sales";
            case "asc", "price_asc" -> "asc";
            case "desc", "price_desc" -> "desc";
            case "popular", "rating" -> sort.strip();
            default -> throw new IllegalArgumentException("Cách sắp xếp không hợp lệ.");
        };
    }

    @Override
    public List<CatalogProductResponse> topProducts(Long shopId) {
        CatalogBrowseFilter filter = new CatalogBrowseFilter();
        filter.setShopId(shopId);
        filter.setSort("sales");
        return catalogService.listProducts(filter).stream().limit(5).toList();
    }

    @Override
    public StorefrontShopHomeResponse shopHome(Long shopId) {
        Page<CatalogProductResponse> products =
                searchShopProducts(shopId, new StorefrontCatalogFilter(), 1, PageSizes.DEFAULT);
        return new StorefrontShopHomeResponse(
                products.getTotalElements(),
                products.stream().limit(5).toList(),
                topProducts(shopId));
    }

    @Override
    public List<CatalogCategory> categories() {
        return catalogService.categories().stream()
                .sorted(Comparator.comparing(CatalogCategory::getCategoryId))
                .toList();
    }

    @Override
    public long productCount(Long shopId) {
        return searchShopProducts(shopId, new StorefrontCatalogFilter(), 1, PageSizes.DEFAULT)
                .getTotalElements();
    }

    @Override
    public List<StorefrontShopOption> shops() {
        return storefrontShopRepository.list();
    }

    @Override
    public List<String> shippingPlaces() {
        return shops().stream()
                .map(StorefrontShopOption::place)
                .filter(place -> place != null && !place.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public String categoryName(Long categoryId) {
        return categories().stream()
                .filter(category -> category.getCategoryId().equals(categoryId))
                .map(CatalogCategory::getName)
                .findFirst()
                .orElse("Tất cả sản phẩm");
    }

    @Override
    public StorefrontProductDetail productDetail(Long variantId, Long productId, String slug) {
        CatalogProductResponse product = catalogService.getProduct(variantId, productId, slug);
        Shop shop = shopBrowseService.profile(product.getShopId());
        CatalogBrowseFilter filters = new CatalogBrowseFilter();
        filters.setShopId(product.getShopId());
        Page<CatalogProductResponse> related = catalogService.listProducts(filters);
        return new StorefrontProductDetail(
                product,
                shop,
                catalogDetailRepository.find(product.getProductId()),
                shopAddress(shop.getShopId()).orElse("Chưa cập nhật địa chỉ"),
                shop.getCreatedAt() == null ? null : shop.getCreatedAt().plusHours(7).toLocalDate(),
                related.getTotalElements(),
                related.stream()
                        .filter(item -> !item.getProductId().equals(product.getProductId()))
                        .limit(5)
                        .toList());
    }

    @Override
    public Optional<String> shopAddress(Long shopId) {
        return storefrontShopRepository.formattedAddress(shopId);
    }
}
