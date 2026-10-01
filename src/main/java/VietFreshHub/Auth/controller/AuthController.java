package VietFreshHub.Auth.controller;

import VietFreshHub.Auth.dto.RegisterRequest;
import VietFreshHub.Auth.dto.RegisterResponse;
import VietFreshHub.Auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordEncoder passwordEncoder;
    @GetMapping("/register")
    public String showRegisterForm(Model model, CsrfToken csrfToken) {
        csrfToken.getToken();
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }
    @GetMapping("/")
    public String redirectToLogin() {
        return "redirect:/login";
    }
    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        RegisterResponse registerResponse = authService.register(request);

        // Tài khoản vừa đăng ký luôn được gán ROLE_CUSTOMER
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        registerResponse.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                );

        // Đưa Authentication vào SecurityContext
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);


        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                registerResponse.getMessage()
        );
        //        return "redirect:/";
        return "redirect:" + getHomeUrl(authentication);
//        return "customer/home";
    }
    @GetMapping("/login")
    public String showLoginPage(CsrfToken csrfToken) {
        csrfToken.getToken();
        String demoPassword = "Demo@123";
        System.out.println(passwordEncoder.encode(demoPassword));
        return "auth/login";
    }
    @PostMapping("/login")
    public String login(
            @RequestParam("username") String email,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        Authentication authentication =
                authService.login(email, password);

        if (authentication == null) {
            model.addAttribute(
                    "errorMessage",
                    "Email hoặc mật khẩu không đúng"
            );
            return "auth/login";
        }

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                request,
                response
        );

//        return "redirect:/";
//        return "customer/home";
//        return "redirect:" + getHomeUrl(authentication);
        return   getHomeUrl(authentication);
    }
    private String getHomeUrl(Authentication authentication) {
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

        return "/access-denied";
    }

    private boolean hasAuthority(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
