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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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
    @GetMapping(value="/{shopOrderId}",params="!shopOrderId")
    public String detail(@PathVariable Long shopOrderId,
                         Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var order=orders.getShopDetails(customerId,shopOrderId);
        var receipt=orders.getDetails(customerId,order.orderId());
        model.addAttribute("order",order);
        model.addAttribute("receipt",receipt);
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customerId));
        model.addAttribute("shopOrderId",shopOrderId);
        model.addAttribute("canRetryVnpayPayment",vnpayPayments.canRetry(order.orderId(),customerId));
        model.addAttribute("actions",lifecycle.shopActions(customerId,shopOrderId));
        model.addAttribute("refund",refunds.getCustomerShopState(shopOrderId,authentication));
        model.addAttribute("receiptRefund",refunds.getCustomerState(order.orderId(),authentication));
        return "customer/order-detail";
    }
    @GetMapping(value="/{orderId}",params="shopOrderId")
    public String legacyDetail(@PathVariable Long orderId,@RequestParam Long shopOrderId,
                               Authentication authentication) {
        var receipt=orders.getDetails(currentCustomer.requireCustomerId(authentication),orderId);
        if(receipt.shops().stream().noneMatch(shop -> shop.shopOrderId().equals(shopOrderId))) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
        }
        return "redirect:/customer/orders/"+shopOrderId;
    }
    @GetMapping("/shops/{shopOrderId}")
    public String shopDetail(@PathVariable Long shopOrderId,Authentication authentication) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        orders.getShopDetails(customerId,shopOrderId);
        return "redirect:/customer/orders/"+shopOrderId;
    }
    @GetMapping("/{orderId}/confirmation")
    public String confirmation(@PathVariable Long orderId,Authentication authentication,RedirectAttributes flash) {
        orders.getDetails(currentCustomer.requireCustomerId(authentication),orderId);
        flash.addFlashAttribute("successMessage","Đặt hàng thành công.");
        return "redirect:/customer/orders";
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view;
    }
}
