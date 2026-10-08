package VietFreshHub.Cart.controller;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.dto.StockCheckResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    private final CurrentCustomerService currentCustomer;
    @PostMapping("/carts/items")
    public AddToCartResponse addToCart(Authentication authentication, @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(currentCustomer.requireCustomerId(authentication), request);
    }
    @GetMapping("/carts/active")
    public CartDto getActiveCart(Authentication authentication) {
        return cartService.getActiveCart(currentCustomer.requireCustomerId(authentication));
    }
    @GetMapping("/products/variants/{variantId}/stock")
    public StockCheckResponse getVariantStock(@PathVariable Long variantId) {
        return cartService.getVariantStock(variantId);
    }
}
