package VietFreshHub.coupon.controller;

import VietFreshHub.coupon.services.CouponApplicationService;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manager/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_MANAGER')")
public class CouponUsageController {

    private final CouponApplicationService couponApplicationService;
    private final ShopService shopService;

    @GetMapping("/{couponId}/usages")
    public String usageHistory(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        var usagePage = couponApplicationService.getUsageHistory(
                shopId,
                couponId,
                PageRequest.of(Math.max(page, 0), 10)
        );

        model.addAttribute("couponId", couponId);
        model.addAttribute("usagePage", usagePage);

        return "coupons/coupon-usage-history";
    }
}