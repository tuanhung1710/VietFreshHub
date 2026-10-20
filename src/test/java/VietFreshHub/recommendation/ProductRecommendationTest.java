package VietFreshHub.recommendation;

import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.recommendation.dto.ProductRecommendation;
import VietFreshHub.recommendation.repository.ProductRecommendationRepository;
import VietFreshHub.recommendation.service.impl.ProductRecommendationServiceImpl;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRecommendationTest {
    final ProductRecommendationRepository reads = mock(ProductRecommendationRepository.class);
    final CatalogProductRepository products = mock(CatalogProductRepository.class);
    final CatalogService catalog = mock(CatalogService.class);
    final ProductRecommendationServiceImpl service = new ProductRecommendationServiceImpl(reads, products, catalog);
    CatalogProduct source;
    @BeforeEach void setup() {
        source = new CatalogProduct(); source.setProductId(1L);
        Shop shop = new Shop(); shop.setShopId(2L); source.setShop(shop);
        CatalogCategory category = new CatalogCategory(); category.setCategoryId(3L); category.setStatus("ACTIVE");
        source.setCategories(Set.of(category));
        when(reads.findByProductId(1L)).thenReturn(Optional.of(source));
        when(catalog.isVisible(source)).thenReturn(true);
        when(products.findCatalogCategories()).thenReturn(List.of(category));
    }
    @Test void invalidSourceDoesNotQuery() { assertTrue(service.relatedTo(null).isEmpty()); verifyNoInteractions(reads, products, catalog); }
    @Test void hiddenSourceHasNoRecommendations() {
        when(catalog.isVisible(source)).thenReturn(false); assertTrue(service.relatedTo(1L).isEmpty()); verifyNoInteractions(products);
    }
    @Test void missingSourceHasNoRecommendations() {
        assertTrue(service.relatedTo(999L).isEmpty()); verifyNoInteractions(catalog, products);
    }
    @Test void inactiveCategoryTreeHasNoRecommendations() {
        when(products.findCatalogCategories()).thenReturn(List.of()); assertTrue(service.relatedTo(1L).isEmpty());
        verify(reads, never()).findCandidates(any(), any(), anySet(), anySet(), any(), any());
    }
    @Test void preservesRankAndReturnsOneAvailableVariantPerProduct() {
        CatalogProduct first = new CatalogProduct(); first.setProductId(20L);
        CatalogProduct second = new CatalogProduct(); second.setProductId(10L);
        when(reads.findCandidates(eq(1L), eq(2L), eq(Set.of(3L)), eq(Set.of(3L)), any(), any())).thenReturn(List.of(first, second));
        var ten = new ProductRecommendation(10L, 100L, "Ten", "A", "Shop", null, BigDecimal.TEN);
        var twenty = new ProductRecommendation(20L, 200L, "Twenty", "A", "Shop", null, BigDecimal.ONE);
        var duplicate = new ProductRecommendation(20L, 201L, "Twenty", "B", "Shop", null, BigDecimal.TEN);
        when(reads.findAvailableVariants(eq(List.of(20L,10L)), any())).thenReturn(List.of(ten, twenty, duplicate));
        assertEquals(List.of(twenty, ten), service.relatedTo(1L));
        verify(reads).findCandidates(eq(1L), eq(2L), anySet(), anySet(), any(), argThat(p -> p.getPageSize()==6));
    }
    @Test void variantNoLongerAvailableIsNotRendered() {
        CatalogProduct candidate = new CatalogProduct(); candidate.setProductId(20L);
        when(reads.findCandidates(any(), any(), anySet(), anySet(), any(), any())).thenReturn(List.of(candidate));
        assertTrue(service.relatedTo(1L).isEmpty());
    }
}
