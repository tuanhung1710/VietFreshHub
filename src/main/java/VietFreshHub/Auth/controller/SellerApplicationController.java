package VietFreshHub.Auth.controller;

import VietFreshHub.Auth.dto.SellerApplicationForm;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService applicationService;
    private final UserRepository userRepository;

    @GetMapping("/customer/apply")
    public String showForm(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);

        model.addAttribute("applicant", user);
        model.addAttribute("sellerApplicationForm", new SellerApplicationForm());

        return "customer/application";
    }

    @PostMapping(
            value = "/customer/apply",
            consumes = "multipart/form-data"
    )
    public String submit(
            @Valid @ModelAttribute("sellerApplicationForm")
            SellerApplicationForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        User user = getCurrentUser(authentication);

        if (form.getIdentityCard() == null || form.getIdentityCard().isEmpty()) {
            bindingResult.rejectValue(
                    "identityCard",
                    "required",
                    "Vui lòng tải giấy tờ tùy thân"
            );
        }

        if (form.getBusinessLicense() == null
                || form.getBusinessLicense().isEmpty()) {
            bindingResult.rejectValue(
                    "businessLicense",
                    "required",
                    "Vui lòng tải giấy phép hoặc chứng nhận"
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("applicant", user);
            return "customer/application";
        }

        try {
            applicationService.submit(user, form);
        } catch (IllegalArgumentException | IOException e) {
            bindingResult.reject("uploadError", e.getMessage());
            model.addAttribute("applicant", user);
            return "customer/application";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Hồ sơ đã được gửi để xét duyệt."
        );

        return "redirect:/customer/apply/success";
    }

    @GetMapping("/customer/apply/success")
    public String showSuccessPage() {
        return "customer/home";
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản đang đăng nhập"
                ));
    }
}