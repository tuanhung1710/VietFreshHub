package VietFreshHub.subscription.controller;

import VietFreshHub.shop.service.ShopService;
import VietFreshHub.subscription.dto.SubscriptionPaymentForm;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import VietFreshHub.subscription.services.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    @GetMapping("/plans")
    public String availablePlans(
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        populatePlansPage(model, shopId);

        if (!model.containsAttribute("paymentForm")) {
            model.addAttribute(
                    "paymentForm",
                    new SubscriptionPaymentForm()
            );
        }

        return "subscription/shop-subscription-plans";
    }

    @PostMapping("/payments")
    public String createPaymentRequest(
            Authentication authentication,
            @Valid @ModelAttribute("paymentForm")
            SubscriptionPaymentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        if (bindingResult.hasErrors()) {
            populatePlansPage(model, shopId);
            return "subscription/shop-subscription-plans";
        }

        try {
            var payment = subscriptionService.createPaymentRequest(
                    shopId,
                    form
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã tạo yêu cầu thanh toán. "
                            + "Gói sẽ được kích hoạt sau khi xác nhận nhận tiền."
            );

            return "redirect:/seller/subscription/payments/"
                    + payment.subscriptionPaymentId();

        } catch (IllegalArgumentException ex) {
            bindingResult.reject(
                    "subscription.payment.invalid",
                    ex.getMessage()
            );

            populatePlansPage(model, shopId);

            return "subscription/shop-subscription-plans";
        }
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

    private void populatePlansPage(Model model, Long shopId) {
        model.addAttribute("plans", planService.getActivePlans());

        model.addAttribute(
                "currentSubscription",
                subscriptionService.getCurrentSubscription(shopId)
                        .orElse(null)
        );
    }
}