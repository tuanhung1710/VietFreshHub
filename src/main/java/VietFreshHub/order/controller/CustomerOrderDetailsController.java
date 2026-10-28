package VietFreshHub.order.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.dto.OrderTrackingResponse;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.CustomerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer/orders")
public class CustomerOrderDetailsController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderQueryService orders;
    private final CustomerOrderService lifecycle;

    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Authentication authentication, Model model) {
        return render(orderId, authentication, model, false);
    }

    @GetMapping("/{orderId}/confirmation")
    public String confirmation(@PathVariable Long orderId, Authentication authentication, Model model) {
        return render(orderId, authentication, model, true);
    }

    private String render(Long orderId, Authentication authentication, Model model, boolean confirmation) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        OrderDetailsResponse order = orders.getDetails(customerId, orderId);
        model.addAttribute("order", order);
        Map<Long, OrderTrackingResponse.ShopTracking> trackingShops = Map.of();
        if (!order.shops().isEmpty()) {
            OrderTrackingResponse tracking = lifecycle.tracking(customerId, orderId);
            if (tracking != null) {
                trackingShops = tracking.shops().stream().collect(Collectors.toMap(
                        OrderTrackingResponse.ShopTracking::shopOrderId, shop -> shop));
            }
        }
        model.addAttribute("trackingShops", trackingShops);
        model.addAttribute("cartBadgeTotal", orders.getCartBadgeTotal(customerId));
        model.addAttribute("confirmation", confirmation);
        model.addAttribute("actions", lifecycle.actions(customerId, orderId));
        return "customer/order-detail";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        ModelAndView view = new ModelAndView("customer/order-not-found");
        view.setStatus(HttpStatus.NOT_FOUND);
        return view;
    }
}
