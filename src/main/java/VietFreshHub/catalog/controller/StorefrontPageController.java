package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.StorefrontProductDetail;
import VietFreshHub.catalog.service.StorefrontService;

import lombok.RequiredArgsConstructor;

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

    private final StorefrontService storefrontService;

    /** Hiển thị sản phẩm, các quy cách mua được và sản phẩm liên quan trong cùng cửa hàng. */
    @GetMapping({"", "/{variantId}"})
    public String detail(
            @PathVariable(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String slug,
            Model model) {
        StorefrontProductDetail detail =
                storefrontService.productDetail(variantId, productId, slug);
        model.addAttribute("product", detail.product());
        model.addAttribute("shop", detail.shop());
        model.addAttribute("details", detail.details());
        model.addAttribute("formattedAddress", detail.formattedAddress());
        model.addAttribute("joinedDate", detail.joinedDate());
        model.addAttribute("shopProductCount", detail.shopProductCount());
        model.addAttribute("related", detail.related());
        return "customer/product-detail";
    }
}
