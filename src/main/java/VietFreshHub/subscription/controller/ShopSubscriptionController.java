package VietFreshHub.subscription.controller;

import VietFreshHub.shop.service.ShopService;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import VietFreshHub.subscription.services.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/seller/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_MANAGER')")
public class ShopSubscriptionController {

    private final ShopSubscriptionService subscriptionService;
    private final SubscriptionPlanService planService;
    private final ShopService shopService;

    @GetMapping
    public String currentSubscription(
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "currentSubscription",
                subscriptionService.getCurrentSubscription(shopId)
                        .orElse(null)
        );

        model.addAttribute("freePlan", planService.getFreePlan());

        return "subscription/shop-subscription";
    }

    @GetMapping("/payments/{paymentId}")
    public String paymentDetail(
            Authentication authentication,
            @PathVariable Long paymentId,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "payment",
                subscriptionService.getPayment(shopId, paymentId)
        );

        return "subscription/subscription-payment-detail";
    }

    @GetMapping("/payments")
    public String paymentHistory(
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "payments",
                subscriptionService.getPaymentHistory(shopId)
        );

        return "subscription/subscription-payment-history";
    }

    @GetMapping("/history")
    public String subscriptionHistory(
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "subscriptions",
                subscriptionService.getSubscriptionHistory(shopId)
        );

        return "subscription/shop-subscription-history";
    }


}