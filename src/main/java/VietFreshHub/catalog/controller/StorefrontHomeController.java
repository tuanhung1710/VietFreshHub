package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.service.StorefrontService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class StorefrontHomeController {

    private final StorefrontService storefrontService;

    /** Hiển thị trang chủ với gợi ý có phân trang, sản phẩm bán chạy và các cửa hàng đang mở. */
    @GetMapping({"/home", "/customer/home"})
    public String home(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        model.addAttribute(
                "rows",
                storefrontService.searchProducts(new StorefrontCatalogFilter(), page, size));
        var topProducts = storefrontService.topProducts(null);
        model.addAttribute("topProducts", topProducts);
        model.addAttribute(
                "heroImageUrl",
                topProducts.stream()
                        .map(CatalogProductResponse::getImageUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .filter(url -> !url.equals("/images/product/product-placeholder.svg"))
                        .findFirst()
                        .orElse(null));
        model.addAttribute("shops", storefrontService.shops());
        return "customer/home";
    }
}
