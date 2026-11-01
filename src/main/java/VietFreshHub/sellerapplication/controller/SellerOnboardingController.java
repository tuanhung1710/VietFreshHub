package VietFreshHub.sellerapplication.controller;

import VietFreshHub.auth.dto.PendingRegistration;
import VietFreshHub.auth.dto.RegisterRequest;
import VietFreshHub.auth.exception.RegistrationException;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.auth.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SellerOnboardingController {

    private static final String PENDING_SELLER_REGISTRATION = "pendingSellerRegistration";
    private static final Logger log = LoggerFactory.getLogger(SellerOnboardingController.class);

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/seller-onboarding/register")
    public String showRegistrationForm(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return redirectAuthenticatedUser(authentication);
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        return "sellerapplication/register";
    }

    @PostMapping("/seller-onboarding/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (isAuthenticated(authentication)) {
            return redirectAuthenticatedUser(authentication);
        }

        if (bindingResult.hasErrors()) {
            return "sellerapplication/register";
        }

        try {
            authService.validateSellerApplicantRegistration(request);
            PendingRegistration pending = emailVerificationService.startRegistration(request);
            session.setAttribute(PENDING_SELLER_REGISTRATION, pending);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Mã xác thực đã được gửi đến email của bạn."
            );
            return "redirect:/seller-onboarding/verify-email";
        } catch (RegistrationException exception) {
            bindingResult.rejectValue(exception.getField(), "duplicate", exception.getMessage());
            return "sellerapplication/register";
        } catch (IllegalStateException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            return "sellerapplication/register";
        } catch (MailException exception) {
            log.error("Không gửi được OTP đăng ký bán hàng", exception);
            model.addAttribute(
                    "errorMessage",
                    "Chưa gửi được email OTP. Hãy kiểm tra cấu hình mail và thử lại."
            );
            return "sellerapplication/register";
        }
    }

    @GetMapping("/seller-onboarding/verify-email")
    public String showVerifyEmail(HttpSession session, Model model) {
        PendingRegistration pending = getPendingRegistration(session);
        if (pending == null) {
            return "redirect:/seller-onboarding/register";
        }
        model.addAttribute("email", pending.getEmail());
        return "sellerapplication/verify-email";
    }

    @PostMapping("/seller-onboarding/verify-email")
    public String verifyEmail(
            @RequestParam("otp") String otp,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        PendingRegistration pending = getPendingRegistration(session);
        if (pending == null) {
            return "redirect:/seller-onboarding/register";
        }

        if (!emailVerificationService.verifyOtp(pending, otp)) {
            model.addAttribute("email", pending.getEmail());
            model.addAttribute(
                    "errorMessage",
                    "Mã OTP không đúng, đã hết hạn hoặc bạn đã nhập sai quá số lần cho phép."
            );
            return "sellerapplication/verify-email";
        }

        try {
            authService.registerSellerApplicant(pending);
            Authentication authentication =
                    authService.loginAfterEmailVerification(pending.getEmail());

            if (authentication == null) {
                session.removeAttribute(PENDING_SELLER_REGISTRATION);
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Xác minh thành công. Vui lòng đăng nhập để tiếp tục."
                );
                return "redirect:/login";
            }

            session.removeAttribute(PENDING_SELLER_REGISTRATION);
            saveAuthentication(authentication, request, response);
            return "redirect:/seller-onboarding/apply";
        } catch (RegistrationException | IllegalStateException exception) {
            model.addAttribute("email", pending.getEmail());
            model.addAttribute("errorMessage", exception.getMessage());
            return "sellerapplication/verify-email";
        }
    }

    @PostMapping("/seller-onboarding/verify-email/resend")
    public String resendOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        PendingRegistration pending = getPendingRegistration(session);
        if (pending == null) {
            return "redirect:/seller-onboarding/register";
        }

        try {
            emailVerificationService.resendOtp(pending);
            redirectAttributes.addFlashAttribute("successMessage", "Mã OTP mới đã được gửi.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (MailException exception) {
            log.error("Không gửi lại được OTP đăng ký bán hàng", exception);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chưa gửi được email. Vui lòng thử lại sau."
            );
        }

        return "redirect:/seller-onboarding/verify-email";
    }

    private PendingRegistration getPendingRegistration(HttpSession session) {
        return (PendingRegistration) session.getAttribute(PENDING_SELLER_REGISTRATION);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String redirectAuthenticatedUser(Authentication authentication) {
        if (authentication.getAuthorities().isEmpty()) {
            return "redirect:/customer/seller-applications";
        }
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER"))) {
            return "redirect:/customer/apply";
        }
        return "redirect:/";
    }

    private void saveAuthentication(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
