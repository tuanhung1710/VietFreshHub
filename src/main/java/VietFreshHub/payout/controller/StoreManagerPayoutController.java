package VietFreshHub.payout.controller;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.payout.service.SellerPayoutService;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/payout-account")
public class StoreManagerPayoutController {

    private final SellerPayoutService sellerPayoutService;
    private final ShopService shopService;
    private final AuthService authService;

    @GetMapping
    public String account(Authentication authentication, Model model) {
        Long shopId = shopService.getManagedShopId(authentication);
        model.addAttribute("payoutAccount", sellerPayoutService.getShopPayoutAccount(shopId));
        return "store_manager/payout-account";
    }

    @PostMapping
    public String save(@RequestParam String bankCode,
                       @RequestParam String bankName,
                       @RequestParam String accountNumber,
                       @RequestParam String accountHolder,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            Long shopId = shopService.getManagedShopId(authentication);
            sellerPayoutService.saveShopAccount(shopId, bankCode, bankName, accountNumber,
                    accountHolder, authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã lưu tài khoản payout. Nếu bạn thay đổi thông tin, admin sẽ cần xác minh lại.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:/seller/payout-account";
    }
}
