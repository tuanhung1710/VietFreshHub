package VietFreshHub.payment.controller;

import VietFreshHub.payment.service.VnpayPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Controller @RequiredArgsConstructor @Slf4j
@RequestMapping("/payments/vnpay")
public class VnpayCallbackController {
    private final VnpayPaymentService payments;

    @GetMapping("/ipn") @ResponseBody
    public ResponseEntity<Map<String,String>> ipn(@RequestParam MultiValueMap<String,String> data) {
        Map<String,String> reply;
        try { reply=payments.receiveIpn(data); }
        catch(RuntimeException ex) {
            log.warn("VNPAY IPN could not commit, errorType={}",ex.getClass().getSimpleName());
            reply=Map.of("RspCode","99","Message","Please retry");
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(reply);
    }

    @GetMapping("/return")
    public String returned(@RequestParam MultiValueMap<String,String> data,Model model,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("Referrer-Policy","no-referrer");
        boolean valid=false;
        try { valid=payments.validReturn(data); } catch(RuntimeException ignored) { /* Generic display only. */ }
        model.addAttribute("valid",valid);
        // Browser return never writes payment state or exposes customer/order data anonymously.
        return "customer/vnpay-return";
    }
}
