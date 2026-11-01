package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopBrowseService;
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
    private final VietFreshHub.shop.service.ShopReviewService reviews;
    private final VietFreshHub.shop.service.ShopOfferService offers;
    private final VietFreshHub.shop.service.ShopFavoriteService favorites;

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shopsPage", shops.list(page, keyword));
        model.addAttribute("keyword", keyword);
        return "shop/list";
    }

    @GetMapping("/{shopId}")
    public String profile(@PathVariable Long shopId, org.springframework.security.core.Authentication authentication, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("location", shops.location(shopId).orElse(null));
        model.addAttribute("reviewSummary", reviews.summary(shopId));
        boolean customer = authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        model.addAttribute("favorite", customer && favorites.isFavorite(authentication, shopId));
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
