package VietFreshHub.auth.controller;

import VietFreshHub.auth.dto.ForgotPasswordRequest;
import VietFreshHub.auth.dto.ResetPasswordRequest;
import VietFreshHub.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordRequest());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(
            @Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String resetBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/reset-password")
                .build()
                .toUriString();
        passwordResetService.requestReset(form.getEmail(), resetBaseUrl);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Nếu email hợp lệ và đã xác minh, hướng dẫn đặt lại mật khẩu sẽ được gửi. Hãy kiểm tra hộp thư của bạn."
        );
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(
            @RequestParam(required = false) String token,
            Model model
    ) {
        ResetPasswordRequest form = new ResetPasswordRequest();
        form.setToken(token);
        model.addAttribute("resetPasswordForm", form);
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute(
                    "errorMessage",
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới."
            );
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid @ModelAttribute("resetPasswordForm") ResetPasswordRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()
                && !form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.confirm.mismatch",
                    "Mật khẩu xác nhận không khớp."
            );
        }

        if (!bindingResult.hasErrors()) {
            try {
                passwordResetService.resetPassword(form.getToken(), form.getNewPassword());
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Mật khẩu đã được đặt lại. Bạn có thể đăng nhập bằng mật khẩu mới."
                );
                return "redirect:/login";
            } catch (IllegalArgumentException exception) {
                bindingResult.rejectValue("token", "password.reset.invalid", exception.getMessage());
            }
        }

        return "auth/reset-password";
    }
}
