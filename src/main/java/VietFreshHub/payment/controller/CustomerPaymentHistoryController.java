package VietFreshHub.payment.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.dto.PaymentHistoryFilter;
import VietFreshHub.payment.dto.PaymentHistoryRow;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.service.CustomerPaymentHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer/payments")
public class CustomerPaymentHistoryController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerPaymentHistoryService payments;
    private final CustomerOrderQueryService orders;

    @GetMapping
    public String history(@Valid @ModelAttribute("filter") PaymentHistoryFilter filter, BindingResult errors,
                           Authentication authentication, Model model) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        model.addAttribute("invalidFilter", errors.hasErrors());
        if (errors.hasErrors()) {
            model.addAttribute("paymentPage", Page.empty());
            model.addAttribute("filterError", "Bộ lọc không hợp lệ. Kiểm tra trang, trạng thái và ngày lọc rồi thử lại.");
        } else {
            Page<PaymentHistoryRow> result = payments.history(customerId, filter);
            model.addAttribute("paymentPage", result);
            if (filter.getPage() != result.getNumber() + 1) {
                model.addAttribute("pageNotice", "Đang hiển thị trang " + (result.getNumber() + 1) + ".");
            }
        }
        model.addAttribute("statusOptions", statusOptions());
        model.addAttribute("cartBadgeTotal", orders.getCartBadgeTotal(customerId));
        return "customer/payment-history";
    }

    private Map<String, String> statusOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Payment.Status status : Payment.Status.values()) {
            String label;
            if (status == Payment.Status.PENDING) {
                label = "Chờ thanh toán / Chờ thu COD";
            } else {
                label = OrderLabels.paymentTransaction(status);
            }
            options.put(status.name(), label);
        }
        return options;
    }
}
