package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopBrowseService;
import VietFreshHub.shop.service.ShopReviewService;
import VietFreshHub.shop.service.ShopOfferService;
import VietFreshHub.shop.service.ShopFavoriteService;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
@RequestMapping("/shops")
public class ShopBrowseController {
    private final ShopBrowseService shops;
    private final ShopReviewService reviews;
    private final ShopOfferService offers;
    private final ShopFavoriteService favorites;
    private final CatalogService catalog;

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shopsPage", shops.list(page, keyword));
        model.addAttribute("keyword", keyword);
        return "shop/list";
    }

    @GetMapping("/{shopId}")
    public String profile(@PathVariable Long shopId, Authentication authentication, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("location", shops.location(shopId).orElse(null));
        model.addAttribute("reviewSummary", reviews.summary(shopId));
        boolean customer = authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        model.addAttribute("favorite", customer && favorites.isFavorite(authentication, shopId));
        var filter = new CatalogBrowseFilter();
        filter.setShopId(shopId);
        model.addAttribute("featuredProducts", catalog.listProducts(filter).getContent());
        return "shop/detail";
    }

    @GetMapping("/{shopId}/reviews")
    public String reviews(@PathVariable Long shopId, @RequestParam(defaultValue = "1") int page,
                          @RequestParam(required = false) Integer rating, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("reviewsPage", reviews.list(shopId, rating, page));
        model.addAttribute("summary", reviews.summary(shopId));
        model.addAttribute("rating", rating);
        return "shop/reviews";
    }

    @GetMapping("/{shopId}/products")
    public String products(@PathVariable Long shopId) {
        shops.profile(shopId);
        return "redirect:/home?shopId=" + shopId;
    }

    @GetMapping("/{shopId}/offers")
    public String offers(@PathVariable Long shopId, @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("offersPage", offers.list(shopId, page));
        return "shop/offers";
    }
}
