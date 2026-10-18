package VietFreshHub.subscription.controller;

import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/shop-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopSubscriptionController {

    private final ShopSubscriptionService subscriptionService;

    @GetMapping
    public String subscriptions(
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        var pageable = PageRequest.of(
                Math.max(page, 0),
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("subscriptionId")
                )
        );

        model.addAttribute(
                "subscriptionPage",
                subscriptionService.getAllSubscriptions(pageable)
        );

        return "subscription/admin-shop-subscriptions";
    }

    @GetMapping("/payments")
    public String payments(
            @RequestParam(defaultValue = "PENDING")
            SubscriptionPaymentStatus status,
            Model model
    ) {
        model.addAttribute(
                "payments",
                subscriptionService.getPaymentsByStatus(status)
        );

        model.addAttribute("selectedStatus", status);
        model.addAttribute(
                "paymentStatuses",
                SubscriptionPaymentStatus.values()
        );

        return "subscription/admin-subscription-payments";
    }


}