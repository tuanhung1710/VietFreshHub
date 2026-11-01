package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/profile")
public class ShopProfileController {
    private final ShopProfileService profiles;

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("shop", profiles.getManagedProfile(authentication));
        return "store_manager/profile";
    }
}
