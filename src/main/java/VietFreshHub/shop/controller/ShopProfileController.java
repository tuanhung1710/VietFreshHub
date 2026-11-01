package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import VietFreshHub.shop.dto.ShopProfileRequest;

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

    @GetMapping("/edit")
    public String edit(Authentication authentication, Model model) {
        var shop = profiles.getManagedProfile(authentication);
        var form = new ShopProfileRequest();
        form.setShopName(shop.getShopName());
        form.setDescription(shop.getDescription());
        form.setPhone(shop.getPhone());
        form.setEmail(shop.getEmail());
        form.setLogoUrl(shop.getLogoUrl());
        form.setBannerUrl(shop.getBannerUrl());
        model.addAttribute("profileForm", form);
        return "store_manager/profile-form";
    }

    @PostMapping
    public String update(Authentication authentication,
                         @Valid @ModelAttribute("profileForm") ShopProfileRequest form,
                         BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) return "store_manager/profile-form";
        profiles.update(authentication, form);
        flash.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ cửa hàng.");
        return "redirect:/seller/profile";
    }
}
