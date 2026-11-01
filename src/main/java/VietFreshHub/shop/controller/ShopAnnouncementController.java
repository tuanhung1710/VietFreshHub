package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ShopAnnouncementController {
    private final ShopAnnouncementService announcements;

    @GetMapping("/seller/announcements")
    public String list(Authentication authentication, @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("announcementsPage", announcements.list(authentication, page));
        return "store_manager/announcements";
    }
}
