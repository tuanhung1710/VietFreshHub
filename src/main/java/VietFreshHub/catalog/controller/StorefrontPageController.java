package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.service.ShopBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/product-detail")
public class StorefrontPageController {

    private final CatalogService catalogService;
    private final ShopBrowseService shopBrowseService;
    private final StorefrontService storefrontService;

    /** Hiển thị sản phẩm, các quy cách mua được và sản phẩm liên quan trong cùng cửa hàng. */
    @GetMapping({"", "/{variantId}"})
    public String detail(
            @PathVariable(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String slug,
            Model model) {
        CatalogProductResponse product = catalogService.getProduct(variantId, productId, slug);
        Shop shop = shopBrowseService.profile(product.getShopId());
        CatalogBrowseFilter filters = new CatalogBrowseFilter();
        filters.setShopId(product.getShopId());
        Page<CatalogProductResponse> related = catalogService.listProducts(filters);
        model.addAttribute("product", product);
        model.addAttribute("shop", shop);
        model.addAttribute("details", storefrontService.productDetails(product.getProductId()));
        model.addAttribute("formattedAddress", storefrontService.shopAddress(shop.getShopId()).orElse("Chưa cập nhật địa chỉ"));
        model.addAttribute("joinedDate", shop.getCreatedAt() == null ? null : shop.getCreatedAt().plusHours(7).toLocalDate());
        model.addAttribute("shopProductCount", related.getTotalElements());
        model.addAttribute("related", related.stream()
                .filter(item -> !item.getProductId().equals(product.getProductId())).limit(5).toList());
        return "customer/product-detail";
    }
}
