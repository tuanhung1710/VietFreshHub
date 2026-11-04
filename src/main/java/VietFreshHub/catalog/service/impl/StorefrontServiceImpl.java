package VietFreshHub.catalog.service.impl;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.dto.StorefrontCategoryResponse;
import VietFreshHub.catalog.dto.StorefrontHomeResponse;
import VietFreshHub.catalog.dto.StorefrontOptionsResponse;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.dto.StorefrontShopHomeResponse;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.repository.CatalogDetailRepository;
import VietFreshHub.catalog.repository.StorefrontShopRepository;
import VietFreshHub.catalog.repository.StorefrontShopRepository.ShopOption;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.common.PageResponse;
import VietFreshHub.common.PageSizes;
import VietFreshHub.common.Pagination;
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

    @Override
    public Page<CatalogProductResponse> searchProducts(StorefrontCatalogFilter filters, int page, int size) {
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
    private CatalogBrowseFilter prepareFilters(StorefrontCatalogFilter filters, int page, int size) {
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
        Page<CatalogProductResponse> products = searchShopProducts(
                shopId, new StorefrontCatalogFilter(), 1, PageSizes.DEFAULT);
        return new StorefrontShopHomeResponse(products.getTotalElements(),
                products.stream().limit(5).toList(), topProducts(shopId));
    }

    @Override
    public List<CatalogCategory> categories() {
        return catalogService.categories().stream()
                .sorted(Comparator.comparing(CatalogCategory::getCategoryId)).toList();
    }

    @Override
    public long productCount(Long shopId) {
        return searchShopProducts(shopId, new StorefrontCatalogFilter(), 1, PageSizes.DEFAULT).getTotalElements();
    }

    @Override
    public List<ShopOption> shops() {
        return storefrontShopRepository.list();
    }

    @Override
    public List<String> shippingPlaces() {
        return shops().stream().map(ShopOption::place)
                .filter(place -> place != null && !place.isBlank()).distinct().toList();
    }

    @Override
    public String categoryName(Long categoryId) {
        return categories().stream().filter(category -> category.getCategoryId().equals(categoryId))
                .map(CatalogCategory::getName).findFirst().orElse("Tất cả sản phẩm");
    }

    @Override
    public CatalogDetailRepository.Details productDetails(Long productId) {
        return catalogDetailRepository.find(productId);
    }

    @Override
    public Optional<String> shopAddress(Long shopId) {
        return storefrontShopRepository.formattedAddress(shopId);
    }

    /** Giữ cấu trúc JSON của API cũ; các controller MVC không dùng các phản hồi này. */
    @Override
    public StorefrontOptionsResponse options() {
        List<StorefrontCategoryResponse> categories = categories().stream()
                .map(category -> new StorefrontCategoryResponse(category.getCategoryId(), category.getName())).toList();
        return new StorefrontOptionsResponse(categories, shops(), PageSizes.OPTIONS, PageSizes.DEFAULT);
    }

    @Override
    public StorefrontHomeResponse home(CatalogBrowseFilter paging) {
        CatalogBrowseFilter suggestions = new CatalogBrowseFilter();
        suggestions.setPage(paging.getPage());
        suggestions.setPageSize(paging.getPageSize());
        return new StorefrontHomeResponse(products(suggestions), topProducts(null).stream().map(StorefrontProduct::from).toList());
    }

    @Override
    public PageResponse<StorefrontProduct> products(CatalogBrowseFilter filter) {
        return PageResponse.from(catalogService.listProducts(filter).map(StorefrontProduct::from));
    }

    @Override
    public StorefrontProduct product(Long productId, Long variantId) {
        return StorefrontProduct.from(catalogService.getProduct(variantId, productId, null));
    }
}
