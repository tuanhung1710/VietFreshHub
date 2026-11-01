package VietFreshHub.cart.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.dto.UpdateCartQuantityRequest;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.CatalogProductResponse.VariantResponse;
import VietFreshHub.catalog.service.CatalogService;
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
    private final CatalogService catalogService;
    private final CurrentCustomerService currentCustomer;
    private final ProductRecommendationService recommendations;

    @GetMapping("/customer/home")
    public String showHomePage(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "") String keyword,
                               @RequestParam(defaultValue = "") String category,
                               Authentication authentication, Model model) {
        if (page < 1 || page > 10000) {
            return "redirect:/home";
        }
        if (keyword.length() > 100) {
            model.addAttribute("errorMessage", "Từ khóa tìm kiếm tối đa 100 ký tự.");
            keyword = "";
        }
        model.addAttribute("cart", populateCartBadge(authentication));

        String searchKeyword = keyword.trim();
        Page<CatalogProductResponse> products;
        if (category.isBlank()) {
            products = catalogService.listProducts(page, searchKeyword);
        } else {
            products = catalogService.listProducts(page, searchKeyword, category);
        }
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("category", category);
        model.addAttribute("products", products.getContent());
        model.addAttribute("productPage", products);
        model.addAttribute("keyword", searchKeyword);
        return "customer/home";
    }

    @GetMapping({"/product-detail", "/product-detail/{variantId}"})
    public String showProductDetail(@PathVariable(required = false) Long variantId,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) String slug,
                                     Authentication authentication, Model model) {
        model.addAttribute("cart", populateCartBadge(authentication));
        CatalogProductResponse product = catalogService.getProduct(variantId, productId, slug);
        VariantResponse selectedVariant = product.getSelectedVariant();

        Long selectedVariantId = null;
        int availableStock = 0;
        boolean purchasable = false;
        if (selectedVariant != null) {
            selectedVariantId = selectedVariant.getVariantId();
            availableStock = selectedVariant.getAvailableStock();
            purchasable = selectedVariant.isPurchasable();
        }
        model.addAttribute("product", product);
        model.addAttribute("recommendations", recommendations.relatedTo(product.getProductId()));
        model.addAttribute("variants", product.getVariants());
        model.addAttribute("selectedVariant", selectedVariant);
        model.addAttribute("variantId", selectedVariantId);
        model.addAttribute("availableStock", availableStock);
        model.addAttribute("purchasable", purchasable);
        return "customer/product-detail";
    }

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
                Long customerId = currentCustomer.requireCustomerId(authentication);
                cartService.addToCart(customerId, form);
                flash.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng.");
            } catch (OutOfStockException | ResourceNotFoundException | IllegalArgumentException exception) {
                flash.addFlashAttribute("errorMessage", exception.getMessage());
                flash.addFlashAttribute("requestedQuantity", form.getQuantity());
            }
        }
        if ("detail".equals(redirect) && form.getVariantId() != null && form.getVariantId() > 0) {
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
