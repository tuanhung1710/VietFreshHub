package VietFreshHub.Cart.controller;

import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.CartDto;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.dto.StockCheckResponse;
import VietFreshHub.Product.entity.Product;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.repository.ProductRepository;
import VietFreshHub.Product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CustomerCartViewController {

    private final CartService cartService;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @ModelAttribute("cart")
    public CartDto populateActiveCart() {
        return cartService.getActiveCart(1L);
    }

    @GetMapping({"/home", "/customer/home"})
    public String showHomePage(Model model) {
        List<Product> products = productRepository.findAll();
        model.addAttribute("products", products);
        return "customer/home";
    }

    @GetMapping({"/product-detail", "/product-detail/{variantId}"})
    public String showProductDetail(
            @PathVariable(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String slug,
            Model model) {
        ProductVariant selectedVariant = null;

        // 1. Prioritize slug lookup if provided (e.g. ?slug=sau-rieng)
        if (slug != null && !slug.isBlank()) {
            final String searchKey = slug.toLowerCase().trim();
            Product product = productRepository.findBySlug(searchKey)
                    .or(() -> productRepository.findAll().stream()
                            .filter(p -> (p.getSlug() != null && p.getSlug().toLowerCase().contains(searchKey)) ||
                                         (p.getName() != null && p.getName().toLowerCase().contains(searchKey)))
                            .findFirst())
                    .orElse(null);

            if (product != null && product.getVariants() != null && !product.getVariants().isEmpty()) {
                if (variantId != null) {
                    selectedVariant = product.getVariants().stream()
                            .filter(v -> v.getVariantId().equals(variantId))
                            .findFirst()
                            .orElseGet(() -> getLowestPriceVariant(product));
                } else {
                    selectedVariant = getLowestPriceVariant(product);
                }
            }
        }

        // 2. By variantId if slug was not specified
        if (selectedVariant == null && variantId != null) {
            selectedVariant = productVariantRepository.findById(variantId).orElse(null);
        }

        // 3. By productId if specified
        if (selectedVariant == null && productId != null) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product != null && product.getVariants() != null && !product.getVariants().isEmpty()) {
                selectedVariant = getLowestPriceVariant(product);
            }
        }

        // 4. Default fallback: first product in DB that has variants
        if (selectedVariant == null) {
            List<Product> allProducts = productRepository.findAll();
            for (Product p : allProducts) {
                if (p.getVariants() != null && !p.getVariants().isEmpty()) {
                    selectedVariant = getLowestPriceVariant(p);
                    break;
                }
            }
        }

        if (selectedVariant != null) {
            Product product = selectedVariant.getProduct();
            List<ProductVariant> variants = product.getVariants();
            StockCheckResponse stockData = cartService.getVariantStock(selectedVariant.getVariantId());

            model.addAttribute("product", product);
            model.addAttribute("variants", variants);
            model.addAttribute("selectedVariant", selectedVariant);
            model.addAttribute("variantId", selectedVariant.getVariantId());
            model.addAttribute("availableStock", stockData.getAvailableStock());
            model.addAttribute("purchasable", stockData.isPurchasable());
        }

        return "customer/product-detail";
    }

    private ProductVariant getLowestPriceVariant(Product product) {
        return product.getVariants().stream()
                .filter(v -> v.getPrice() != null)
                .min(Comparator.comparing(ProductVariant::getPrice))
                .orElse(product.getVariants().get(0));
    }

    @GetMapping("/cart")
    public String showCartPage() {
        return "customer/cart";
    }

    @PostMapping("/cart/add")
    public String handleAddToCart(
            @RequestParam Long variantId,
            @RequestParam(defaultValue = "1") Integer quantity,
            @RequestParam(required = false) String redirect,
            RedirectAttributes redirectAttributes) {
        try {
            AddToCartRequest request = AddToCartRequest.builder()
                    .variantId(variantId)
                    .quantity(quantity)
                    .build();
            cartService.addToCart(1L, request);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        if ("detail".equalsIgnoreCase(redirect)) {
            return "redirect:/product-detail/" + variantId;
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String handleUpdateQuantity(
            @RequestParam Long variantId,
            @RequestParam Integer quantity,
            RedirectAttributes redirectAttributes) {
        try {
            cartService.updateCartItemQuantity(1L, variantId, quantity);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String handleRemoveItem(
            @RequestParam Long variantId,
            RedirectAttributes redirectAttributes) {
        try {
            cartService.removeCartItem(1L, variantId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }
}
