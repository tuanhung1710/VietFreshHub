package VietFreshHub.catalog;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.repository.CartItemRepository;
import VietFreshHub.cart.service.impl.CartServiceImpl;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.exception.OutOfStockException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorefrontCartPriceTest {
    @Test
    void cartUsesDisplayedOfferPriceAndOnlyItsDiscountedQuantity() {
        var carts = mock(CartRepository.class);
        var items = mock(CartItemRepository.class);
        var variants = mock(CatalogVariantRepository.class);
        var pricing = mock(CatalogPricingService.class);
        var users = mock(UserRepository.class);
        var catalog = mock(CatalogService.class);
        var service = new CartServiceImpl(carts,items,variants,pricing,users,catalog);
        var user = new User(); user.setStatus("ACTIVE");
        var variant = new CatalogVariant(); variant.setVariantId(7L); variant.setPrice(new BigDecimal("50000"));
        var cart = new Cart(); cart.setCartId(1L);
        when(users.findByUserIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(variants.findById(7L)).thenReturn(Optional.of(variant));
        when(catalog.isPurchasable(variant)).thenReturn(true);
        when(carts.findByUserUserIdAndStatus(1L,"ACTIVE")).thenReturn(Optional.of(cart));
        when(pricing.offers(List.of(7L))).thenReturn(Map.of(7L,new CatalogPricingService.Offer(new BigDecimal("30000"),variant.getPrice(),2)));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.addToCart(1L,new AddToCartRequest(7L,2));
        assertEquals(0,result.getUnitPriceSnapshot().compareTo(new BigDecimal("30000")));
        assertEquals(0,result.getItemSubtotal().compareTo(new BigDecimal("60000")));
        assertThrows(OutOfStockException.class,() -> service.addToCart(1L,new AddToCartRequest(7L,3)));
    }
}
