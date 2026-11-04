package VietFreshHub.loyalty.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.loyalty.services.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/customer/loyalty")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final CurrentCustomerService currentCustomer;

    @GetMapping
    public String loyalty(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Long customerId =
                currentCustomer.requireCustomerId(authentication);

        model.addAttribute(
                "balance",
                loyaltyService.getBalance(customerId)
        );

        model.addAttribute(
                "transactionPage",
                loyaltyService.getTransactionHistory(
                        customerId,
                        PageRequest.of(Math.max(page, 0), 10)
                )
        );

        return "loyalty/loyalty-account";
    }
}