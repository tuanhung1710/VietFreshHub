package VietFreshHub.order.controller;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.HttpStatus;
import VietFreshHub.payment.vnpay.VnpayPaymentService;
@Controller @RequiredArgsConstructor
@RequestMapping("/customer/orders")
public class CustomerOrderDetailsController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderQueryService orders;
    private final VietFreshHub.order.service.CustomerOrderService lifecycle;
    private final VnpayPaymentService vnpayPayments;
    private final VietFreshHub.refund.service.RefundRequestService refunds;
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId,Authentication authentication,Model model) {
        return render(orderId,authentication,model,false);
    }
    @GetMapping("/shops/{shopOrderId}")
    public String shopDetail(@PathVariable Long shopOrderId,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        model.addAttribute("order",orders.getShopDetails(customerId,shopOrderId));
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customerId));
        model.addAttribute("shopOrderId",shopOrderId);
        model.addAttribute("confirmation",false);
        model.addAttribute("canRetryVnpayPayment",false);
        model.addAttribute("actions",lifecycle.shopActions(customerId,shopOrderId));
        model.addAttribute("refund",refunds.getCustomerShopState(shopOrderId,authentication));
        return "customer/order-detail";
    }
    @GetMapping("/{orderId}/confirmation")
    public String confirmation(@PathVariable Long orderId,Authentication authentication,Model model) {
        return render(orderId,authentication,model,true);
    }
    private String render(Long orderId,Authentication authentication,Model model,boolean confirmation) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        model.addAttribute("order",orders.getDetails(customerId,orderId));
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customerId));
        model.addAttribute("confirmation",confirmation);
        model.addAttribute("canRetryVnpayPayment",vnpayPayments.canRetry(orderId,customerId));
        model.addAttribute("refund",refunds.getCustomerState(orderId,authentication));
        return "customer/order-detail";
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view;
    }
}
