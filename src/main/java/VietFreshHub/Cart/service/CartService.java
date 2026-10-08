package VietFreshHub.Cart.service;

import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.AddToCartResponse;
import VietFreshHub.Product.dto.StockCheckResponse;

public interface CartService {

    /**
     * Adds a product variant to the customer's active shopping cart.
     * Validates real-time inventory and variant status according to GBR-12, GBR-13, GBR-19, GBR-21.
     *
     * @param userId  ID of the authenticated user
     * @param request Add to cart request DTO
     * @return AddToCartResponse with item details and success message
     */
    AddToCartResponse addToCart(Long userId, AddToCartRequest request);

    /**
     * Retrieves the current active cart and items for a customer.
     */
    VietFreshHub.Cart.dto.CartDto getActiveCart(Long userId);

    /**
     * Fetches real-time stock availability for a product variant.
     */
    StockCheckResponse getVariantStock(Long variantId);

    /**
     * Updates quantity of a cart item for user's active cart.
     */
    void updateCartItemQuantity(Long userId, Long variantId, int newQuantity);

    /**
     * Removes a cart item from user's active cart.
     */
    void removeCartItem(Long userId, Long variantId);
    void clearCart(Long userId);
}
