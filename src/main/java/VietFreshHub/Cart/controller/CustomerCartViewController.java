package VietFreshHub.Cart.controller;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class CustomerCartViewController {
    private final CartService cartService;
    private final CatalogService catalogService;
    private final CurrentCustomerService currentCustomer;
    private final VietFreshHub.Checkout.port.ShippingQuotePort shipping;

    private CartDto populateActiveCart(Authentication authentication) {
        boolean customer = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        return customer ? cartService.getActiveCart(currentCustomer.requireCustomerId(authentication))
                : CartDto.builder().totalItems(0).totalAmount(BigDecimal.ZERO).build();
    }
    @GetMapping({"/home", "/customer/home"})
    public String showHomePage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "") String keyword,
                              Authentication authentication, Model model) {
        if (page < 1 || page > 10000) return "redirect:/home";
        if (keyword.length() > 100) {
            model.addAttribute("errorMessage", "Từ khóa tìm kiếm tối đa 100 ký tự.");
            keyword = "";
        }
        model.addAttribute("cart", populateActiveCart(authentication));
        var products = catalogService.listProducts(page, keyword.trim());
        model.addAttribute("products", products.getContent());
        model.addAttribute("productPage", products);
        model.addAttribute("keyword", keyword.trim());
        return "customer/home";
    }
    @GetMapping({"/product-detail", "/product-detail/{variantId}"})
    public String showProductDetail(@PathVariable(required = false) Long variantId,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) String slug, Authentication authentication, Model model) {
        model.addAttribute("cart", populateActiveCart(authentication));
        var product = catalogService.getProduct(variantId, productId, slug);
        var selected = product.getSelectedVariant();
        model.addAttribute("product", product);
        model.addAttribute("variants", product.getVariants());
        model.addAttribute("selectedVariant", selected);
        model.addAttribute("variantId", selected == null ? null : selected.getVariantId());
        model.addAttribute("availableStock", selected == null ? 0 : selected.getAvailableStock());
        model.addAttribute("purchasable", selected != null && selected.isPurchasable());
        return "customer/product-detail";
    }
    @GetMapping("/cart")
    public String showCartPage(Authentication authentication, Model model) {
        CartDto cart = populateActiveCart(authentication);
        model.addAttribute("cart", cart);
        var eligible=cart.getItems().stream().filter(CartItemDto::isPurchasable).toList();
        var fee=shipping.feePerShop();
        long shopCount=eligible.stream().map(CartItemDto::getShopId).distinct().count();
        model.addAttribute("selectableCount",eligible.size()); model.addAttribute("shippingFeePerShop",fee);
        model.addAttribute("selectedShippingFee",fee==null ? null : fee.multiply(BigDecimal.valueOf(shopCount)));
        model.addAttribute("selectedGrandTotal",fee==null ? cart.getTotalAmount() : cart.getTotalAmount().add(fee.multiply(BigDecimal.valueOf(shopCount))));
        model.addAttribute("shopGroups", cart.getItems().stream().collect(Collectors.groupingBy(
                CartItemDto::getShopId, java.util.LinkedHashMap::new, Collectors.toList())));
        return "customer/cart";
    }
    @PostMapping("/cart/add")
    public String handleAddToCart(@Valid @ModelAttribute("form") AddToCartRequest form, BindingResult errors,
            Authentication authentication, @RequestParam(required = false) String redirect, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("errorMessage", validationMessage(errors));
            flash.addFlashAttribute("requestedQuantity", form.getQuantity());
        }
        else {
            try {
                cartService.addToCart(currentCustomer.requireCustomerId(authentication), form);
                flash.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng.");
            } catch (OutOfStockException | ResourceNotFoundException | IllegalArgumentException ex) {
                flash.addFlashAttribute("errorMessage", ex.getMessage());
                flash.addFlashAttribute("requestedQuantity", form.getQuantity());
            }
        }
        if ("detail".equals(redirect) && form.getVariantId() != null && form.getVariantId() > 0)
            return "redirect:/product-detail/" + form.getVariantId();
        return "redirect:/cart";
    }
    @PostMapping("/cart/update")
    public String handleUpdateQuantity(@Valid @ModelAttribute("form") UpdateCartQuantityRequest form,
            BindingResult errors, Authentication authentication, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", validationMessage(errors));
        else {
            try {
                cartService.updateCartItemQuantity(currentCustomer.requireCustomerId(authentication), form.getVariantId(), form.getQuantity());
                flash.addFlashAttribute("successMessage", form.getQuantity() == 0 ? "Đã xóa sản phẩm khỏi giỏ hàng." : "Đã cập nhật số lượng.");
            } catch (OutOfStockException | ResourceNotFoundException | IllegalArgumentException ex) {
                flash.addFlashAttribute("errorMessage", ex.getMessage());
            }
        }
        return "redirect:/cart";
    }
    @PostMapping("/cart/remove")
    public String handleRemoveItem(@RequestParam Long variantId, Authentication authentication, RedirectAttributes flash) {
        cartService.removeCartItem(currentCustomer.requireCustomerId(authentication), variantId);
        flash.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
        return "redirect:/cart";
    }
    @PostMapping("/cart/clear")
    public String handleClearCart(Authentication authentication, RedirectAttributes flash) {
        cartService.clearCart(currentCustomer.requireCustomerId(authentication));
        flash.addFlashAttribute("successMessage", "Đã xóa toàn bộ sản phẩm khỏi giỏ hàng.");
        return "redirect:/cart";
    }
    private String validationMessage(BindingResult errors) {
        var error = errors.getAllErrors().getFirst();
        return "typeMismatch".equals(error.getCode()) ? "Vui lòng nhập mã sản phẩm và số lượng là số nguyên hợp lệ."
                : error.getDefaultMessage();
    }
}
