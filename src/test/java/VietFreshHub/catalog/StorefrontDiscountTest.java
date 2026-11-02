package VietFreshHub.catalog;

import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.catalog.service.impl.CatalogServiceImpl;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StorefrontDiscountTest {
    @ParameterizedTest
    @CsvSource({
            "80000, 100000, 20", "65000.00, 100000.00, 35", "10000, 30000, 66",
            "99999, 100000, 0", "10000, 9999999999999999, 99", "0, 100000, 100"
    })
    void calculatesSavingsFromTheCompareAtPriceWithoutOverstatingThem(BigDecimal price, BigDecimal reference, int expected) {
        var variant = CatalogProductResponse.VariantResponse.builder().price(price).compareAtPrice(reference).build();
        assertEquals(expected, variant.getDiscountPercent());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "80000, null", "80000, 0", "80000, -100000", "80000, 80000", "80000, 70000",
            "null, 100000", "-1, 100000"
    }, nullValues = "null")
    void invalidOrMissingReferencePricesDoNotProduceADiscount(BigDecimal price, BigDecimal reference) {
        var variant = CatalogProductResponse.VariantResponse.builder().price(price).compareAtPrice(reference).build();
        assertNull(variant.getDiscountPercent());
    }

    @Test
    void catalogAndStorefrontUseTheStoredReferenceAndTheSelectedVariantsEffectivePrice() {
        var products = mock(CatalogProductRepository.class);
        var variants = mock(CatalogVariantRepository.class);
        var pricing = mock(CatalogPricingService.class);
        var service = new CatalogServiceImpl(products, variants, pricing,
                mock(CatalogBrowseRepository.class));
        var shop = new Shop();
        shop.setShopId(1L);
        var product = new CatalogProduct();
        product.setProductId(1L);
        product.setName("Xoài");
        product.setImageUrl("/images/xoai-cat.png");
        product.setStatus("ACTIVE");
        product.setShop(shop);
        var category = new CatalogCategory();
        category.setCategoryId(1L);
        product.getCategories().add(category);
        when(products.findCatalogCategories()).thenReturn(List.of(category));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        var discounted = variant(product, 3L, "80000", "100000");
        discounted.setThumbnailUrl("/images/variants/selected.jpg");
        var regular = variant(product, 4L, "90000", null);
        when(variants.findById(4L)).thenReturn(Optional.of(regular));
        product.getVariants().addAll(List.of(discounted, regular));
        when(pricing.offers(anyList())).thenReturn(Map.of(
                3L, new CatalogPricingService.Offer(new BigDecimal("65000"), discounted.getPrice(), 5),
                4L, new CatalogPricingService.Offer(regular.getPrice(), regular.getPrice(), 7)));

        var response = service.getProduct(null, 1L, null);
        var card = StorefrontProduct.from(response);
        assertEquals(new BigDecimal("100000"), card.compareAtPrice());
        assertEquals(new BigDecimal("65000"), card.price());
        assertEquals(35, card.discountPercent());
        assertEquals(3L, card.variantId());
        assertEquals("/images/variants/selected.jpg", card.imageUrl());

        var regularCard = StorefrontProduct.from(service.getProduct(4L, 1L, null));
        assertEquals(4L, regularCard.variantId());
        assertNull(regularCard.compareAtPrice());
        assertNull(regularCard.discountPercent());
        assertEquals("/images/xoai-cat.png", regularCard.imageUrl());
    }

    private CatalogVariant variant(CatalogProduct product, long id, String price, String reference) {
        var variant = new CatalogVariant();
        variant.setProduct(product);
        variant.setVariantId(id);
        variant.setPrice(new BigDecimal(price));
        variant.setCompareAtPrice(reference == null ? null : new BigDecimal(reference));
        return variant;
    }
}
