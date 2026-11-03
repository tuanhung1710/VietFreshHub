package VietFreshHub.auth.controller;

import VietFreshHub.auth.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class HeaderAccountAdvice {
    private final UserRepository users;

    @ModelAttribute("headerAccountName")
    public String accountName(Authentication authentication, HttpServletRequest request) {
        // Trang lỗi không truy vấn tài khoản, kể cả khi cơ sở dữ liệu đang gặp sự cố.
        if (request.getDispatcherType() == DispatcherType.ERROR || "/error".equals(request.getServletPath())) {
            return null;
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .map(user -> user.getFullName() == null || user.getFullName().isBlank()
                        ? authentication.getName() : user.getFullName().trim())
                .orElse(authentication.getName());
    }
}
