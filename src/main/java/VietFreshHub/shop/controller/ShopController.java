package VietFreshHub.shop.controller;

import VietFreshHub.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ShopController {

    private final OrderService orderService;
    private final VietFreshHub.shop.service.ShopService shopService;
    private final VietFreshHub.product.service.ProductService productService;

    @GetMapping("/seller")
    public String showSellerPage(Authentication authentication, Model model) {
        model.addAttribute("shopCode", shopService.getManagedShopCode(authentication));
        model.addAttribute("orderCounts", orderService.getOrderCounts(authentication));
        model.addAttribute("inventoryAttention", productService.inventoryAttentionSummary(authentication));
        return "store_manager/shop";
    }
}
