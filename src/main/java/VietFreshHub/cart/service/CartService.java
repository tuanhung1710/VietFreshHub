package VietFreshHub.cart.service;

import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.AddToCartResponse;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.catalog.dto.StockCheckResponse;

public interface CartService {

    AddToCartResponse addToCart(Long userId, AddToCartRequest request);

    CartDto getActiveCart(Long userId);

    StockCheckResponse getVariantStock(Long variantId);

    void updateCartItemQuantity(Long userId, Long variantId, int newQuantity);

    void removeCartItem(Long userId, Long variantId);

    void clearCart(Long userId);
}
