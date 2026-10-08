package VietFreshHub.Order.controller;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Order.service.CustomerOrderQueryService;
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
    private final CartService carts;
    private final VietFreshHub.Order.service.CustomerOrderService lifecycle;
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
        model.addAttribute("order",orders.getDetails(customerId,orderId));
        model.addAttribute("cart",carts.getActiveCart(customerId));
        model.addAttribute("confirmation",confirmation);
        model.addAttribute("actions",lifecycle.actions(customerId,orderId));
        return "customer/order-detail";
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view;
    }
}
