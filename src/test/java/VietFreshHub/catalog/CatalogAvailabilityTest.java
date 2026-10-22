package VietFreshHub.catalog;

import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.catalog.service.impl.CatalogServiceImpl;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogAvailabilityTest {
    final CatalogProductRepository products = mock(CatalogProductRepository.class);
    final CatalogVariantRepository variants = mock(CatalogVariantRepository.class);
    final CheckoutInventoryBatchRepository stock = mock(CheckoutInventoryBatchRepository.class);
    final CatalogServiceImpl service = new CatalogServiceImpl(products, variants, stock);
    CatalogProduct product;
    CatalogVariant variant;

    @BeforeEach void setup() {
        product = new CatalogProduct(); product.setProductId(1L);
        product.setStatus("ACTIVE"); product.setApprovalStatus("APPROVED");
        Shop shop = new Shop(); shop.setStatus("ACTIVE"); shop.setAvailabilityStatus("OPEN"); product.setShop(shop);
        CatalogCategory category = new CatalogCategory(); category.setCategoryId(3L); category.setStatus("ACTIVE");
        product.setCategories(Set.of(category)); when(products.findCatalogCategories()).thenReturn(List.of(category));
        variant = new CatalogVariant(); variant.setVariantId(11L); variant.setProduct(product);
        variant.setStatus("ACTIVE"); variant.setPrice(BigDecimal.TEN); product.setVariants(List.of(variant));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(stock.getAvailableStocks(anyList())).thenReturn(Map.of(11L, 0));
    }
    @Test void fullyReservedOrEmptyProductIsHiddenAndDirectLinkIsRejected() {
        assertFalse(service.isVisible(product));
        assertThrows(ResourceNotFoundException.class, () -> service.getProduct(null, 1L, null));
    }
    @Test void releasedStockMakesActiveProductVisibleAgain() {
        assertFalse(service.isVisible(product));
        when(stock.getAvailableStocks(anyList())).thenReturn(Map.of(11L, 2));
        assertTrue(service.isVisible(product));
        assertTrue(service.getProduct(null, 1L, null).getSelectedVariant().isPurchasable());
    }
    @Test void inactiveVariantDoesNotReappearEvenWithStock() {
        variant.setStatus("INACTIVE"); when(stock.getAvailableStocks(anyList())).thenReturn(Map.of(11L, 5));
        assertFalse(service.isVisible(product));
    }
    @Test void unavailableOptionsAreExcludedWhileAnotherVariantIsSellable() {
        CatalogVariant empty = new CatalogVariant(); empty.setVariantId(12L); empty.setProduct(product);
        empty.setStatus("ACTIVE"); empty.setPrice(BigDecimal.ONE);
        product.setVariants(List.of(variant, empty));
        when(stock.getAvailableStocks(anyList())).thenReturn(Map.of(11L, 2, 12L, 0));
        var response = service.getProduct(null, 1L, null);
        assertEquals(List.of(11L), response.getVariants().stream().map(v -> v.getVariantId()).toList());
        assertThrows(ResourceNotFoundException.class, () -> {
            when(variants.findById(12L)).thenReturn(Optional.of(empty)); service.getProduct(12L, null, null);
        });
    }
}
