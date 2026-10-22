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
@Controller @RequiredArgsConstructor
@RequestMapping("/customer/orders")
public class CustomerOrderDetailsController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderQueryService orders;
    private final VietFreshHub.order.service.CustomerOrderService lifecycle;
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId,Authentication authentication,Model model) {
        return render(orderId,authentication,model,false);
    }
    @GetMapping("/{orderId}/confirmation")
    public String confirmation(@PathVariable Long orderId,Authentication authentication,Model model) {
        return render(orderId,authentication,model,true);
    }
    private String render(Long orderId,Authentication authentication,Model model,boolean confirmation) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var order=orders.getDetails(customerId,orderId);
        model.addAttribute("order",order);
        var tracking=order.shops().isEmpty() ? null : lifecycle.tracking(customerId,orderId);
        model.addAttribute("trackingShops",tracking==null ? java.util.Map.of() : tracking.shops().stream()
                .collect(java.util.stream.Collectors.toMap(
                        VietFreshHub.order.dto.OrderTrackingResponse.ShopTracking::shopOrderId,shop -> shop)));
        model.addAttribute("cartBadgeTotal",orders.getCartBadgeTotal(customerId));
        model.addAttribute("confirmation",confirmation);
        model.addAttribute("actions",lifecycle.actions(customerId,orderId));
        return "customer/order-detail";
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view;
    }
}
