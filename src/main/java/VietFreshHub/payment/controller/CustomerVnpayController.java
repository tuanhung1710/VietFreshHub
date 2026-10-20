package VietFreshHub.payment.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.payment.service.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
@RequestMapping("/customer/payments/orders/{orderId}")
public class CustomerVnpayController {
    private final CurrentCustomerService customer;
    private final VnpayPaymentService payments;
    private final VietFreshHub.order.service.CustomerOrderQueryService orders;

    @GetMapping
    public String show(@PathVariable Long orderId,Authentication auth,Model model) {
        model.addAttribute("payment",payments.page(customer.requireCustomerId(auth),orderId));
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customer.requireCustomerId(auth)));
        return "customer/vnpay-payment";
    }

    @GetMapping("/status") @ResponseBody
    public org.springframework.http.ResponseEntity<VietFreshHub.payment.dto.VnpayPaymentPage> status(
            @PathVariable Long orderId,Authentication auth) {
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store")
                .body(payments.page(customer.requireCustomerId(auth),orderId));
    }

    @PostMapping("/pay")
    public String pay(@PathVariable Long orderId,Authentication auth,HttpServletRequest request,RedirectAttributes flash) {
        try {
            // Never trust an arbitrary X-Forwarded-For supplied by the browser.
            return "redirect:"+payments.start(customer.requireCustomerId(auth),orderId,request.getRemoteAddr());
        } catch(CheckoutException ex) {
            flash.addFlashAttribute("errorMessage",ex.getMessage());
            return "redirect:/customer/payments/orders/"+orderId;
        }
    }
}
