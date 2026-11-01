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

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shopsPage", shops.list(page, keyword));
        model.addAttribute("keyword", keyword);
        return "shop/list";
    }

    @GetMapping("/{shopId}")
    public String profile(@PathVariable Long shopId, Model model) {
        model.addAttribute("shop", shops.profile(shopId));
        model.addAttribute("location", shops.location(shopId).orElse(null));
        return "shop/detail";
    }
}
