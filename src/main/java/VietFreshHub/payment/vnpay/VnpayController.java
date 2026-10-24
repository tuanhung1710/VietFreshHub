package VietFreshHub.payment.vnpay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/payment/vnpay")
public class VnpayController {
    private final VnpayPaymentService payments;

    @GetMapping("/return")
    public String paymentReturn(@RequestParam Map<String, String> fields, RedirectAttributes flash) {
        VnpayPaymentService.ReturnResult result = payments.handleReturn(fields);
        if (result.orderId() == null) {
            flash.addFlashAttribute("errorMessage", result.message());
            return "redirect:/customer/orders";
        }
        if (result.successful()) {
            flash.addFlashAttribute("successMessage", result.message());
        } else {
            flash.addFlashAttribute("errorMessage", result.message());
        }
        return "redirect:/customer/orders/" + result.orderId();
    }
}
