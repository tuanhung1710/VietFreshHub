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
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId,@RequestParam(required=false) Long shopOrderId,
                         Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var receipt=orders.getDetails(customerId,orderId);
        var selectedShop=receipt.shops().stream()
                .filter(shop -> shopOrderId==null || shop.shopOrderId().equals(shopOrderId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        if(shopOrderId!=null && receipt.shops().size()==1) {
            return "redirect:/customer/orders/"+orderId;
        }
        Long selectedShopOrderId=selectedShop.shopOrderId();
        model.addAttribute("order",orders.getShopDetails(customerId,selectedShopOrderId));
        model.addAttribute("receipt",receipt);
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customerId));
        model.addAttribute("shopOrderId",selectedShopOrderId);
        model.addAttribute("canRetryVnpayPayment",vnpayPayments.canRetry(orderId,customerId));
        model.addAttribute("actions",lifecycle.shopActions(customerId,selectedShopOrderId));
        model.addAttribute("refund",refunds.getCustomerShopState(selectedShopOrderId,authentication));
        model.addAttribute("receiptRefund",refunds.getCustomerState(orderId,authentication));
        return "customer/order-detail";
    }
    @GetMapping("/shops/{shopOrderId}")
    public String shopDetail(@PathVariable Long shopOrderId,Authentication authentication) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var order=orders.getShopDetails(customerId,shopOrderId);
        var receipt=orders.getDetails(customerId,order.orderId());
        return "redirect:/customer/orders/"+order.orderId()
                +(receipt.shops().size()>1 ? "?shopOrderId="+shopOrderId : "");
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
