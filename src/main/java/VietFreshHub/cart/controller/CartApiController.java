package VietFreshHub.cart.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.AddToCartResponse;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Form HTML cũ vẫn dùng /cart/add; trang chi tiết có JavaScript dùng API này. */
@RestController
@RequiredArgsConstructor
public class CartApiController {
    private final CartService cartService;
    private final CurrentCustomerService currentCustomer;

    public record CartError(String code, Long variantId, Integer requestedQuantity,
                            Integer availableStock, String message) {
    }

    public record CartAdded(Long cartItemId, Integer quantity, long totalItems, String message) {
    }

    @PostMapping("/api/cart/items")
    public ResponseEntity<?> add(@Valid @ModelAttribute AddToCartRequest form, BindingResult errors,
                                 Authentication authentication) {
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().body(new CartError("INVALID_QUANTITY", form.getVariantId(),
                    form.getQuantity(), null, "Vui lòng chọn sản phẩm và nhập số lượng nguyên từ 1 trở lên."));
        }
        Long customerId = currentCustomer.requireCustomerId(authentication);
        try {
            AddToCartResponse result = cartService.addToCart(customerId, form);
            long totalItems = cartService.getActiveCart(customerId).getTotalItems();
            return ResponseEntity.ok(new CartAdded(result.getCartItemId(), result.getQuantity(),
                    totalItems, result.getMessage()));
        } catch (OutOfStockException exception) {
            return ResponseEntity.status(409).body(new CartError("INSUFFICIENT_STOCK", form.getVariantId(),
                    form.getQuantity(), exception.getAvailableStock(), exception.getMessage()));
        } catch (ResourceNotFoundException exception) {
            return ResponseEntity.status(409).body(new CartError("ITEM_UNAVAILABLE", form.getVariantId(),
                    form.getQuantity(), null, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new CartError("INVALID_QUANTITY", form.getVariantId(),
                    form.getQuantity(), null, exception.getMessage()));
        }
    }
}
