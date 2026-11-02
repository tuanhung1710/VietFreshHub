package VietFreshHub.subscription.controller;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/shop-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopSubscriptionController {

    private final ShopSubscriptionService subscriptionService;
    private final UserRepository userRepository;

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

    @PostMapping("/payments/{paymentId}/approve")
    public String approvePayment(
            @PathVariable Long paymentId,
            @RequestParam String transactionReference,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long adminUserId = getCurrentUserId(authentication);

        try {
            subscriptionService.approvePayment(
                    paymentId,
                    adminUserId,
                    transactionReference
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã xác nhận thanh toán và kích hoạt gói cho shop."
            );

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/admin/shop-subscriptions/payments";
    }

    @PostMapping("/payments/{paymentId}/reject")
    public String rejectPayment(
            @PathVariable Long paymentId,
            @RequestParam String reason,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long adminUserId = getCurrentUserId(authentication);

        try {
            subscriptionService.rejectPayment(
                    paymentId,
                    adminUserId,
                    reason
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã đánh dấu yêu cầu thanh toán thất bại."
            );

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/admin/shop-subscriptions/payments";
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Bạn chưa đăng nhập"
            );
        }

        return userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .map(user -> user.getUserId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Không xác định được tài khoản Admin"
                ));
    }
}