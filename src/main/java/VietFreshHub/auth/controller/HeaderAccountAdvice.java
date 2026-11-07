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

    @ModelAttribute("activeChannel")
    public String activeChannel(Authentication authentication, HttpServletRequest request) {
        String path = request.getServletPath();
        if (inPath(path, "/admin")) return "admin";
        if (inPath(path, "/seller") || inPath(path, "/manager") || inPath(path, "/store_manager")) return "seller";
        if (inPath(path, "/delivery")) return "delivery";

        // Shared inbox links carry their channel so buyer and seller tabs stay independent.
        if (inPath(path, "/notifications")) {
            String channel = request.getParameter("channel");
            if ("seller".equals(channel) && hasRole(authentication, "ROLE_STORE_MANAGER")) return "seller";
            if ("customer".equals(channel) && hasRole(authentication, "ROLE_CUSTOMER")) return "customer";
            if (hasRole(authentication, "ROLE_ADMIN")) return "admin";
            if (hasRole(authentication, "ROLE_CUSTOMER")) return "customer";
            if (hasRole(authentication, "ROLE_STORE_MANAGER")) return "seller";
            if (hasRole(authentication, "ROLE_DELIVERY_STAFF")) return "delivery";
        }
        return "customer";
    }

    private boolean inPath(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

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
