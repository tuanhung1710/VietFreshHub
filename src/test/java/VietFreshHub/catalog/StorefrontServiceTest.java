package VietFreshHub.catalog;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.repository.StorefrontShopRepository;
import VietFreshHub.catalog.repository.CatalogDetailRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.common.PageSizes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorefrontServiceTest {
    @Test
    void homeKeepsItsOwnPagingAndQueriesTopFiveSeparately() {
        var catalog = mock(CatalogService.class);
        var service = new StorefrontService(catalog, mock(StorefrontShopRepository.class), mock(CatalogDetailRepository.class));
        var variant = CatalogProductResponse.VariantResponse.builder().variantId(9L).price(BigDecimal.TEN)
                .availableStock(3).build();
        var product = CatalogProductResponse.builder().productId(7L).name("Xoài").selectedVariant(variant).build();
        when(catalog.listProducts(any(CatalogBrowseFilter.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(1, 20), 30), new PageImpl<>(List.of(product)));
        var paging = new CatalogBrowseFilter();
        paging.setPage(2);
        paging.setPageSize(20);
        paging.setShopId(99L);
        var home = service.home(paging);
        assertEquals(2, home.products().page());
        assertEquals(20, home.products().pageSize());
        assertEquals(9L, home.products().items().getFirst().variantId());
        var filters = ArgumentCaptor.forClass(CatalogBrowseFilter.class);
        verify(catalog, times(2)).listProducts(filters.capture());
        assertNull(filters.getAllValues().getFirst().getShopId());
        assertEquals("sales", filters.getAllValues().getLast().getSort());
        assertEquals(1, filters.getAllValues().getLast().getPage());
    }

    @Test
    void pageSizeOptionsComeFromCommon() {
        var catalog = mock(CatalogService.class);
        when(catalog.categories()).thenReturn(List.of());
        var shops = mock(StorefrontShopRepository.class);
        when(shops.list()).thenReturn(List.of());
        var options = new StorefrontService(catalog, shops, mock(CatalogDetailRepository.class)).options();
        assertEquals(PageSizes.OPTIONS, options.pageSizes());
        assertEquals(PageSizes.DEFAULT, options.defaultPageSize());
    }
}
