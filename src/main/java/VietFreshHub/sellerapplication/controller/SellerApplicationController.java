package VietFreshHub.sellerapplication.controller;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.sellerapplication.dto.SellerApplicationForm;
import VietFreshHub.sellerapplication.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
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

    @GetMapping({"/customer/apply", "/seller-onboarding/apply"})
    public String showForm(Authentication authentication, Model model, HttpServletRequest request) {
        User user = getCurrentUser(authentication);

        model.addAttribute("applicant", user);
        model.addAttribute("sellerApplicationForm", new SellerApplicationForm());
        model.addAttribute("applicationAction", applicationAction(request));

        return "customer/application";
    }

    @PostMapping(
            value = {"/customer/apply", "/seller-onboarding/apply"},
            consumes = "multipart/form-data"
    )
    public String submit(
            @Valid @ModelAttribute("sellerApplicationForm")
            SellerApplicationForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        User user = getCurrentUser(authentication);
        String applicationAction = applicationAction(request);

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
            model.addAttribute("applicationAction", applicationAction);
            return "customer/application";
        }

        try {
            applicationService.submit(user, form);
        } catch (IllegalArgumentException | IOException e) {
            bindingResult.reject("uploadError", e.getMessage());
            model.addAttribute("applicant", user);
            model.addAttribute("applicationAction", applicationAction);
            return "customer/application";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Hồ sơ đã được gửi để xét duyệt."
        );

        return request.getServletPath().startsWith("/seller-onboarding/")
                ? "redirect:/customer/seller-applications"
                : "redirect:/customer/apply/success";
    }

    @GetMapping("/customer/apply/success")
    public String showSuccessPage() {
        return "redirect:/customer/home";
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản đang đăng nhập"
                ));
    }

    private String applicationAction(HttpServletRequest request) {
        return request.getServletPath().startsWith("/seller-onboarding/")
                ? "/seller-onboarding/apply"
                : "/customer/apply";
    }
}
