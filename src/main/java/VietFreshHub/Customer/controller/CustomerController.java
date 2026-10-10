package VietFreshHub.customer.controller;

import VietFreshHub.customer.dto.CustomerProfileRequest;
import VietFreshHub.customer.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerProfileService customerProfileService;

    public CustomerController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/home")
    public String home() {
        return "customer/home";
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("profile", customerProfileService.getProfile(authentication.getName()));
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profile") CustomerProfileRequest profile,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                customerProfileService.updateProfile(authentication.getName(), profile);
                redirectAttributes.addFlashAttribute("successMessage", "Thông tin cá nhân đã được cập nhật.");
                return "redirect:/customer/profile";
            } catch (IllegalArgumentException exception) {
                bindingResult.rejectValue("phone", "profile.phone.duplicate", exception.getMessage());
            }
        }

        profile.setEmail(customerProfileService.getProfile(authentication.getName()).getEmail());
        model.addAttribute("profile", profile);
        return "customer/profile";
    }
}
