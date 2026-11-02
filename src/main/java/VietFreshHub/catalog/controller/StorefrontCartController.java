package VietFreshHub.catalog.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/storefront/api/cart")
public class StorefrontCartController {
    public record Summary(long totalItems) { }
    private final CartService carts;
    private final CurrentCustomerService customers;

    @GetMapping
    public Summary summary(Authentication authentication) {
        boolean customer = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        return new Summary(customer ? carts.getActiveCart(customers.requireCustomerId(authentication)).getTotalItems() : 0);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public Summary add(Authentication authentication, @Valid @RequestBody AddToCartRequest request) {
        Long customerId = customers.requireCustomerId(authentication);
        carts.addToCart(customerId, request);
        return new Summary(carts.getActiveCart(customerId).getTotalItems());
    }

    @ExceptionHandler({OutOfStockException.class, ResourceNotFoundException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(RuntimeException exception) { return Map.of("message", exception.getMessage()); }
}
