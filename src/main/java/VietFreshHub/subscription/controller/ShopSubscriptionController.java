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
import jakarta.servlet.http.HttpServletRequest;
import VietFreshHub.payment.vnpay.VnpayProperties;

@Controller
@RequestMapping("/seller/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_MANAGER')")
public class ShopSubscriptionController {

    private final ShopSubscriptionService subscriptionService;
    private final SubscriptionPlanService planService;
    private final ShopService shopService;
    private final VnpayProperties vnpayProperties;

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
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
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

            String returnUrl = subscriptionReturnUrl();
            return "redirect:" + subscriptionService.startVnpayPayment(shopId,
                    payment.subscriptionPaymentId(), request.getRemoteAddr(), returnUrl);

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

    @PostMapping("/payments/{paymentId}/vnpay")
    public String resumeVnpayPayment(Authentication authentication, @PathVariable Long paymentId,
                                     HttpServletRequest request, RedirectAttributes flash) {
        Long shopId = shopService.getManagedShopId(authentication);
        String returnUrl = subscriptionReturnUrl();
        try {
            return "redirect:" + subscriptionService.startVnpayPayment(shopId, paymentId,
                    request.getRemoteAddr(), returnUrl);
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/seller/subscription/payments/" + paymentId;
        }
    }

    private String subscriptionReturnUrl() {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(vnpayProperties.getReturnUrl())
                .replacePath("/payment/vnpay/subscription/return").replaceQuery(null).build().toUriString();
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

    @PostMapping("/{subscriptionId}/cancel")
    public String cancelSubscription(
            Authentication authentication,
            @PathVariable Long subscriptionId,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        try {
            subscriptionService.cancelSubscription(
                    shopId,
                    subscriptionId
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã hủy gói dịch vụ. Shop áp dụng quyền lợi gói Free."
            );

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/seller/subscription";
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
