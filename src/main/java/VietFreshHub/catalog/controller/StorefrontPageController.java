package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.shop.service.ShopBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class StorefrontPageController {
    private final CatalogService catalog;
    private final ShopBrowseService shops;
    private final StorefrontService storefront;

    @GetMapping({"/home", "/customer/home"})
    public String home() { return "customer/home"; }

    @GetMapping("/shops")
    public String shops() { return "customer/home"; }

    @GetMapping("/shops/{shopId}")
    public String shop(@PathVariable Long shopId, Model model) {
        addShopInformation(shopId, model);
        return "shop/detail";
    }

    private void addShopInformation(Long shopId, Model model) {
        var shop = shops.profile(shopId);
        model.addAttribute("shop", shop);
        model.addAttribute("formattedAddress", storefront.shopAddress(shopId).orElse("Chưa cập nhật địa chỉ"));
        model.addAttribute("joinedDate", shop.getCreatedAt() == null ? null
                : shop.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
    }

    @GetMapping({"/product-detail", "/product-detail/{variantId}"})
    public String product(@PathVariable(required = false) Long variantId, @RequestParam(required = false) Long productId,
                          @RequestParam(required = false) String slug, Model model) {
        var product = catalog.getProduct(variantId, productId, slug);
        addShopInformation(product.getShopId(), model);
        var filter = new VietFreshHub.catalog.dto.CatalogBrowseFilter();
        filter.setShopId(product.getShopId());
        var related = catalog.listProducts(filter);
        model.addAttribute("product", product);
        model.addAttribute("details", storefront.productDetails(product.getProductId()));
        model.addAttribute("shopProductCount", related.getTotalElements());
        return "customer/product-detail";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unavailable(ResourceNotFoundException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "customer/catalog-error";
    }
}
