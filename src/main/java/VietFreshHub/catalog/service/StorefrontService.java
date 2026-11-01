package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.repository.StorefrontShopRepository;
import VietFreshHub.catalog.repository.StorefrontShopRepository.ShopOption;
import VietFreshHub.common.PageResponse;
import VietFreshHub.common.PageSizes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorefrontService {
    public record CategoryOption(Long id, String name, String emoji) { }
    public record Options(List<CategoryOption> categories, List<ShopOption> shops, List<Integer> pageSizes, int defaultPageSize) { }
    public record Home(PageResponse<StorefrontProduct> products, List<StorefrontProduct> topProducts) { }
    private final CatalogService catalog;
    private final StorefrontShopRepository shops;

    public Options options() {
        var categories = catalog.categories().stream().sorted(Comparator.comparing(c -> c.getCategoryId()))
                .map(c -> new CategoryOption(c.getCategoryId(), c.getName(), FruitSymbols.forName(c.getName()))).toList();
        return new Options(categories, shops.list(), PageSizes.OPTIONS, PageSizes.DEFAULT);
    }

    public PageResponse<StorefrontProduct> products(CatalogBrowseFilter filter) {
        return PageResponse.from(catalog.listProducts(filter).map(StorefrontProduct::from));
    }

    public Home home(CatalogBrowseFilter paging) {
        var suggestions = new CatalogBrowseFilter();
        suggestions.setPage(paging.getPage());
        suggestions.setPageSize(paging.getPageSize());
        var best = new CatalogBrowseFilter();
        best.setSort("sales");
        // Section size is a content requirement from the template, separate from selectable page sizes.
        best.setPageSize(PageSizes.OPTIONS.stream().filter(size -> size >= 5).findFirst().orElse(PageSizes.DEFAULT));
        return new Home(products(suggestions), catalog.listProducts(best).stream().limit(5).map(StorefrontProduct::from).toList());
    }

    public StorefrontProduct product(Long productId, Long variantId) {
        return StorefrontProduct.from(catalog.getProduct(variantId, productId, null));
    }
}
