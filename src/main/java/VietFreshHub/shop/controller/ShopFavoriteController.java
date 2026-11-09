package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class ShopFavoriteController {
    private final ShopFavoriteService favorites;

    @PostMapping("/shops/{shopId}/favorite")
    public String favorite(@PathVariable Long shopId, @RequestParam boolean favorite,
                           Authentication authentication, RedirectAttributes flash) {
        favorites.setFavorite(authentication, shopId, favorite);
        flash.addFlashAttribute("successMessage", favorite ? "Đã yêu thích cửa hàng." : "Đã bỏ yêu thích cửa hàng.");
        return favorite ? "redirect:/shops/" + shopId : "redirect:/home";
    }
}
