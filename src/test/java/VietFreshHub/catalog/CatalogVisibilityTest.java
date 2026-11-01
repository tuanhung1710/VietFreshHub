package VietFreshHub.catalog;

import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.catalog.service.impl.CatalogServiceImpl;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CatalogVisibilityTest {
    @Test
    void directLinksNeverExposeSoldOutVariantsOrClosedShops() {
        var products = mock(CatalogProductRepository.class);
        var variants = mock(CatalogVariantRepository.class);
        var pricing = mock(CatalogPricingService.class);
        var service = new CatalogServiceImpl(products, variants, pricing, mock(CatalogBrowseRepository.class));
        var shop = new Shop();
        shop.setShopId(1L);
        var product = new CatalogProduct();
        product.setProductId(1L);
        product.setStatus("ACTIVE");
        product.setShop(shop);
        var category = new CatalogCategory();
        category.setCategoryId(7L);
        product.getCategories().add(category);
        when(products.findCatalogCategories()).thenReturn(List.of(category));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        var active = variant(product, 1L, "ACTIVE");
        var soldOut = variant(product, 2L, "OUT_OF_STOCK");
        product.getVariants().addAll(List.of(active, soldOut));
        when(variants.findById(2L)).thenReturn(Optional.of(soldOut));
        when(pricing.offers(anyList())).thenReturn(Map.of(1L, new CatalogPricingService.Offer(BigDecimal.TEN, BigDecimal.TEN, 5),
                2L, new CatalogPricingService.Offer(BigDecimal.ONE, BigDecimal.ONE, 0)));
        assertEquals(1, service.getProduct(null, 1L, null).getVariants().size());
        assertThrows(ResourceNotFoundException.class, () -> service.getProduct(2L, null, null));
        shop.setAvailabilityStatus("CLOSED");
        assertFalse(service.isVisible(product));
        assertThrows(ResourceNotFoundException.class, () -> service.getProduct(null, 1L, null));
    }

    private CatalogVariant variant(CatalogProduct product, long id, String status) {
        var variant = new CatalogVariant();
        variant.setVariantId(id);
        variant.setProduct(product);
        variant.setStatus(status);
        variant.setPrice(BigDecimal.TEN);
        return variant;
    }
}
