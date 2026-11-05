package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopBrowseService;
import VietFreshHub.shop.service.ShopReviewService;
import VietFreshHub.shop.service.ShopOfferService;
import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.dto.StorefrontShopHomeResponse;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.shop.entity.Shop;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

@Controller
@RequiredArgsConstructor
@RequestMapping("/shops")
public class ShopBrowseController {
    private final ShopBrowseService shopBrowseService;
    private final ShopReviewService shopReviewService;
    private final ShopOfferService shopOfferService;
    private final StorefrontService storefrontService;

    /** Hiển thị danh sách cửa hàng đang hoạt động và mở bán. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("shops", storefrontService.shops());
        return "shop/browse";
    }

    /** Hiển thị hồ sơ và các nhóm sản phẩm của cửa hàng. */
    @GetMapping("/{shopId}")
    public String detail(@PathVariable Long shopId, Model model) {
        addShopInformation(shopId, model);
        StorefrontShopHomeResponse summary = storefrontService.shopHome(shopId);
        model.addAttribute("recommended", summary.getRecommended());
        model.addAttribute("topProducts", summary.getTopProducts());
        model.addAttribute("shopProductCount", summary.getProductCount());
        model.addAttribute("activeTab", "home");
        return "shop/detail";
    }

    @GetMapping("/{shopId}/reviews")
    public String reviews(@PathVariable Long shopId, @RequestParam(defaultValue = "1") int page,
                          @RequestParam(required = false) Integer rating, Model model) {
        model.addAttribute("shop", shopBrowseService.profile(shopId));
        model.addAttribute("reviewsPage", shopReviewService.list(shopId, rating, page));
        model.addAttribute("summary", shopReviewService.summary(shopId));
        model.addAttribute("rating", rating);
        return "shop/reviews";
    }

    @GetMapping("/{shopId}/products")
    public String products(
            @PathVariable Long shopId,
            @Valid @ModelAttribute("filters") StorefrontCatalogFilter filters,
            BindingResult errors,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) throws BindException {
        addShopInformation(shopId, model);
        if (errors.hasErrors()) {
            throw new BindException(errors);
        }
        model.addAttribute("rows", storefrontService.searchShopProducts(shopId, filters, page, size));
        model.addAttribute("shopProductCount", storefrontService.productCount(shopId));
        model.addAttribute("q", filters.getQ());
        model.addAttribute("category", filters.getCategory());
        model.addAttribute("activeTab", "products");
        return "shop/products";
    }

    @GetMapping("/{shopId}/offers")
    public String offers(@PathVariable Long shopId, @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("shop", shopBrowseService.profile(shopId));
        model.addAttribute("offersPage", shopOfferService.list(shopId, page));
        return "shop/offers";
    }

    private void addShopInformation(Long shopId, Model model) {
        Shop shop = shopBrowseService.profile(shopId);
        model.addAttribute("shop", shop);
        model.addAttribute("formattedAddress", storefrontService.shopAddress(shopId).orElse("Chưa cập nhật địa chỉ"));
        model.addAttribute("joinedDate", shop.getCreatedAt() == null ? null : shop.getCreatedAt().plusHours(7).toLocalDate());
    }
}
