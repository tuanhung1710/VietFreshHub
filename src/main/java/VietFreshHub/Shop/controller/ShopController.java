package VietFreshHub.Shop.controller;

import VietFreshHub.Order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ShopController {

    private final OrderService orderService;

    @GetMapping("/seller")
    public String showSellerPage(Authentication authentication, Model model) {
        model.addAttribute("orderCounts", orderService.getOrderCounts(authentication));
        return "store_manager/shop";
    }
}
