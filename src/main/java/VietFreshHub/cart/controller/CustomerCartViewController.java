package VietFreshHub.cart.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.dto.UpdateCartQuantityRequest;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.recommendation.service.ProductRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CustomerCartViewController {
    private final CartService cartService;
    private final CurrentCustomerService currentCustomer;
    private final ProductRecommendationService recommendations;

    @GetMapping("/cart")
    public String showCartPage(Authentication authentication, Model model) {
        CartDto cart = populateActiveCart(authentication);
        int selectableCount = 0;
        for (CartItemDto item : cart.getItems()) {
            if (item.isPurchasable()) {
                selectableCount++;
            }
        }
        Map<Long, List<CartItemDto>> shopGroups = groupItemsByShop(cart.getItems());
        model.addAttribute("cart", cart);
        model.addAttribute("selectableCount", selectableCount);
        model.addAttribute("shopGroups", shopGroups);
        return "customer/cart";
    }

    @PostMapping("/cart/add")
    public String handleAddToCart(@Valid @ModelAttribute("form") AddToCartRequest form,
                                  BindingResult errors, Authentication authentication,
                                  @RequestParam(required = false) String redirect, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("errorMessage", validationMessage(errors));
            flash.addFlashAttribute("requestedQuantity", form.getQuantity());
        } else {
            try {
                var added = cartService.addToCart(currentCustomer.requireCustomerId(authentication), form);
                flash.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng.");
                if ("checkout".equals(redirect)) {
                    return "redirect:/customer/checkout?selectionSubmitted=true&itemIds=" + added.getCartItemId();
                }
            } catch (OutOfStockException | ResourceNotFoundException | IllegalArgumentException ex) {
                flash.addFlashAttribute("errorMessage", ex.getMessage());
                flash.addFlashAttribute("requestedQuantity", form.getQuantity());
            }
        }
        if (("detail".equals(redirect) || "checkout".equals(redirect)) && form.getVariantId() != null && form.getVariantId() > 0) {
            return "redirect:/product-detail/" + form.getVariantId();
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String handleUpdateQuantity(@Valid @ModelAttribute("form") UpdateCartQuantityRequest form,
                                       BindingResult errors, Authentication authentication, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("errorMessage", validationMessage(errors));
        } else {
            try {
                Long customerId = currentCustomer.requireCustomerId(authentication);
                cartService.updateCartItemQuantity(customerId, form.getVariantId(), form.getQuantity());
                String successMessage = "Đã cập nhật số lượng.";
                if (form.getQuantity() == 0) {
                    successMessage = "Đã xóa sản phẩm khỏi giỏ hàng.";
                }
                flash.addFlashAttribute("successMessage", successMessage);
            } catch (OutOfStockException | ResourceNotFoundException | IllegalArgumentException exception) {
                flash.addFlashAttribute("errorMessage", exception.getMessage());
            }
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String handleRemoveItem(@RequestParam Long variantId, Authentication authentication,
                                   RedirectAttributes flash) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        cartService.removeCartItem(customerId, variantId);
        flash.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String handleClearCart(Authentication authentication, RedirectAttributes flash) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        cartService.clearCart(customerId);
        flash.addFlashAttribute("successMessage", "Đã xóa toàn bộ sản phẩm khỏi giỏ hàng.");
        return "redirect:/cart";
    }

    private CartDto populateCartBadge(Authentication authentication) {
        long totalItems = 0;
        if (isCustomer(authentication)) {
            Long customerId = currentCustomer.requireCustomerId(authentication);
            totalItems = cartService.countActiveUnits(customerId);
        }
        return CartDto.builder().totalItems(totalItems).totalAmount(BigDecimal.ZERO).build();
    }

    private CartDto populateActiveCart(Authentication authentication) {
        if (isCustomer(authentication)) {
            Long customerId = currentCustomer.requireCustomerId(authentication);
            return cartService.getActiveCart(customerId);
        }
        return CartDto.builder()
                .totalItems(0)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private boolean isCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_CUSTOMER".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, List<CartItemDto>> groupItemsByShop(List<CartItemDto> items) {
        Map<Long, List<CartItemDto>> shopGroups = new LinkedHashMap<>();
        for (CartItemDto item : items) {
            Long shopId = item.getShopId();
            List<CartItemDto> shopItems = shopGroups.get(shopId);
            if (shopItems == null) {
                shopItems = new ArrayList<>();
                shopGroups.put(shopId, shopItems);
            }
            shopItems.add(item);
        }
        return shopGroups;
    }

    private String validationMessage(BindingResult errors) {
        ObjectError error = errors.getAllErrors().getFirst();
        if ("typeMismatch".equals(error.getCode())) {
            return "Vui lòng nhập mã sản phẩm và số lượng là số nguyên hợp lệ.";
        }
        return error.getDefaultMessage();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String unavailableProduct(ResourceNotFoundException exception, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", exception.getMessage());
        return "redirect:/home";
    }
}
