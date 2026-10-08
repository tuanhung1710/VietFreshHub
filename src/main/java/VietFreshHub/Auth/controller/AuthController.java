package VietFreshHub.Auth.controller;

import VietFreshHub.Auth.dto.PendingRegistration;
import VietFreshHub.Auth.dto.RegisterRequest;
import VietFreshHub.Auth.exception.RegistrationException;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Auth.service.EmailVerificationService;
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
public class AuthController {

    private static final String PENDING_REGISTRATION = "pendingRegistration";
    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final EmailVerificationService emailVerificationService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @GetMapping("/")
    public String redirectToLogin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        return "redirect:" + getHomeUrl(authentication);
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.validateRegistration(request);

            // Mail phải gửi thành công mới lưu thông tin đăng ký vào session.
            PendingRegistration pending =
                    emailVerificationService.startRegistration(request);

            session.setAttribute(PENDING_REGISTRATION, pending);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Mã xác thực đã được gửi đến email của bạn."
            );

            return "redirect:/verify-email";

        } catch (RegistrationException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";

        } catch (MailException ex) {
            log.error("Không gửi được OTP đăng ký", ex);
            model.addAttribute(
                    "errorMessage",
                    "Chưa gửi được email OTP. Hãy kiểm tra cấu hình mail và thử lại."
            );
            return "auth/register";
        }
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("username") String email,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        Authentication authentication = authService.login(email, password);
        log.info("Authentication object {}", authentication);
        if (authentication == null) {
            model.addAttribute(
                    "errorMessage",
                    "Email hoặc mật khẩu không đúng"
            );
            return "auth/login";
        }

        saveAuthentication(authentication, request, response);

        // Thêm "redirect:" để chuyển URL trình duyệt tới trang home.
        return "redirect:" + getHomeUrl(authentication);
    }

    @GetMapping("/verify-email")
    public String showVerifyEmail(HttpSession session, Model model) {
        PendingRegistration pending =
                (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);

        if (pending == null) {
            return "redirect:/register";
        }

        model.addAttribute("email", pending.getEmail());
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    public String verifyEmail(
            @RequestParam("otp") String otp,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {

        PendingRegistration pending =
                (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);

        if (pending == null) {
            return "redirect:/register";
        }

        if (!emailVerificationService.verifyOtp(pending, otp)) {
            model.addAttribute("email", pending.getEmail());
            model.addAttribute(
                    "errorMessage",
                    "Mã OTP không đúng, đã hết hạn hoặc bạn đã nhập sai quá số lần cho phép."
            );
            return "auth/verify-email";
        }

        try {
            // Tạo tài khoản sau khi OTP hợp lệ.
            authService.registerVerified(pending);

            // Tạo Authentication cho tài khoản vừa xác minh.
            Authentication authentication =
                    authService.loginAfterEmailVerification(pending.getEmail());

            if (authentication == null) {
                session.removeAttribute(PENDING_REGISTRATION);
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Xác minh thành công. Vui lòng đăng nhập."
                );
                return "redirect:/login";
            }

            session.removeAttribute(PENDING_REGISTRATION);
            saveAuthentication(authentication, request, response);

            return "redirect:" + getHomeUrl(authentication);

        } catch (RegistrationException | IllegalStateException ex) {
            model.addAttribute("email", pending.getEmail());
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/verify-email";
        }
    }

    @PostMapping("/verify-email/resend")
    public String resendOtp(
            HttpSession session,

            RedirectAttributes redirectAttributes) {

        PendingRegistration pending =
                (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);

        if (pending == null) {
            return "redirect:/register";
        }

        try {
            emailVerificationService.resendOtp(pending);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Mã OTP mới đã được gửi."
            );
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        } catch (MailException ex) {
            log.error("Không gửi lại được OTP", ex);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chưa gửi được email. Vui lòng thử lại sau."
            );
        }

        return "redirect:/verify-email";
    }

    private void saveAuthentication(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Đổi session ID sau khi xác thực.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private String getHomeUrl(Authentication authentication) {
        log.info(
                "Home routing for user={}, authorities={}",
                authentication.getName(),
                authentication.getAuthorities()
        );

        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            return "/admin/admin";
        }
        if (hasAuthority(authentication, "ROLE_STORE_MANAGER")) {
            return "/store_manager/shop";
        }
        if (hasAuthority(authentication, "ROLE_DELIVERY_STAFF")) {
            return "/delivery/delivery";
        }
        if (hasAuthority(authentication, "ROLE_CUSTOMER")) {
            return "/customer/home";
        }

        log.warn("Không nhận diện được role của user={}", authentication.getName());
        return "/access-denied";
    }

    private boolean hasAuthority(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals(role));
    }
}