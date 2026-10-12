package VietFreshHub.customer.controller;

import VietFreshHub.customer.dto.CustomerPasswordRequest;
import VietFreshHub.customer.service.CustomerPasswordService;
import jakarta.validation.Valid;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/password")
public class CustomerPasswordController {

    private final CustomerPasswordService customerPasswordService;

    public CustomerPasswordController(CustomerPasswordService customerPasswordService) {
        this.customerPasswordService = customerPasswordService;
    }

    @GetMapping
    public String showForm(Authentication authentication, Model model) {
        model.addAttribute("passwordForm", new CustomerPasswordRequest());
        model.addAttribute("googleLogin", customerPasswordService.isGoogleLogin(authentication));
        return "customer/change-password";
    }

    @PostMapping
    public String changePassword(
            @Valid @ModelAttribute("passwordForm") CustomerPasswordRequest passwordForm,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            if (!passwordForm.getNewPassword().equals(passwordForm.getConfirmPassword())) {
                bindingResult.rejectValue("confirmPassword", "password.confirm.mismatch", "Mật khẩu xác nhận không khớp.");
            } else {
                try {
                    customerPasswordService.changePassword(authentication, passwordForm);
                    redirectAttributes.addFlashAttribute("successMessage", "Mật khẩu đã được thay đổi.");
                    return "redirect:/customer/profile";
                } catch (IllegalArgumentException exception) {
                    bindingResult.rejectValue("currentPassword", "password.current.invalid", exception.getMessage());
                } catch (IllegalStateException exception) {
                    bindingResult.reject("password.account.unsupported", exception.getMessage());
                }
            }
        }

        model.addAttribute("googleLogin", customerPasswordService.isGoogleLogin(authentication));
        model.addAttribute("passwordForm", passwordForm);
        return "customer/change-password";
    }
}
