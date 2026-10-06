package VietFreshHub.Delivery.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DeliveryController {

    @GetMapping("/delivery")
    public String showDeliveryPage() {

        return "delivery/home";
    }
}
