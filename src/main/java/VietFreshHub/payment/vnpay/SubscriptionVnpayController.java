package VietFreshHub.payment.vnpay;

import VietFreshHub.subscription.services.ShopSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/payment/vnpay/subscription")
public class SubscriptionVnpayController {
    private final ShopSubscriptionService subscriptions;

    @GetMapping("/return")
    public String paymentReturn(@RequestParam Map<String, String> fields, RedirectAttributes flash) {
        String result = subscriptions.handleVnpayCallback(fields);
        if (result.startsWith("00|Confirmed")) flash.addFlashAttribute("successMessage", "Kết quả thanh toán Subscription đã được xác minh.");
        else flash.addFlashAttribute("errorMessage", "Chưa xác nhận được thanh toán. Vui lòng kiểm tra lịch sử thanh toán.");
        return "redirect:/seller/subscription/payments";
    }

    @GetMapping("/ipn")
    @ResponseBody
    public Map<String, String> ipn(@RequestParam Map<String, String> fields) {
        String result = subscriptions.handleVnpayCallback(fields);
        int separator = result.indexOf('|');
        return Map.of("RspCode", result.substring(0, separator), "Message", result.substring(separator + 1));
    }
}
