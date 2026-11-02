package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopBrowseService;
import VietFreshHub.shop.service.ShopReviewService;
import VietFreshHub.shop.service.ShopOfferService;
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
        return "redirect:/shops/" + shopId + "?tab=all";
    }

    @GetMapping("/{shopId}/offers")
    public String offers(@PathVariable Long shopId, @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("offersPage", offers.list(shopId, page));
        return "shop/offers";
    }
}
