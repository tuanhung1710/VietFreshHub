package VietFreshHub.Cart.controller;

import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.AddToCartResponse;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.dto.StockCheckResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * UC-15: Add Product Variant to Cart.
     * RESTful Endpoint: POST /api/carts/items
     *
     * @param userId Simulated authenticated userId header/param (In production, extracted from SecurityContext / JWT)
     * @param request AddToCartRequest payload
     * @return AddToCartResponse
     */
    @PostMapping("/carts/items")
    public ResponseEntity<AddToCartResponse> addToCart(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Valid @RequestBody AddToCartRequest request) {

        AddToCartResponse response = cartService.addToCart(userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Fetch active cart for header badge synchronization
     * RESTful Endpoint: GET /api/carts/active
     */
    @GetMapping("/carts/active")
    public ResponseEntity<VietFreshHub.Cart.dto.CartDto> getActiveCart(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        VietFreshHub.Cart.dto.CartDto cart = cartService.getActiveCart(userId);
        return ResponseEntity.ok(cart);
    }

    /**
     * UC-15 Pre-validation: Fetch Real-Time Stock & Availability for Product Detail rendering.
     * RESTful Endpoint: GET /api/products/variants/{variantId}/stock
     *
     * @param variantId ID of variant to check
     * @return StockCheckResponse
     */
    @GetMapping("/products/variants/{variantId}/stock")
    public ResponseEntity<StockCheckResponse> getVariantStock(@PathVariable Long variantId) {
        StockCheckResponse response = cartService.getVariantStock(variantId);
        return ResponseEntity.ok(response);
    }
}
