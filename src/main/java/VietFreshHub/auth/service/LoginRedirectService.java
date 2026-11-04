package VietFreshHub.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;

/** Shares the shopping destination across password login, email verification and Google login. */
@Service
@RequiredArgsConstructor
public class LoginRedirectService implements AuthenticationSuccessHandler {
    private static final String RETURN_TO = LoginRedirectService.class.getName() + ".returnTo";
    private final RequestCache requestCache;

    public void remember(HttpServletRequest request, String returnTo) {
        String target = shoppingPath(returnTo);
        if (target != null) request.getSession(true).setAttribute(RETURN_TO, target);
    }

    public String destination(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        String target = session == null ? null : (String) session.getAttribute(RETURN_TO);
        if (session != null) session.removeAttribute(RETURN_TO);

        var saved = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);
        if (hasRole(authentication, "ROLE_ADMIN")) return home(authentication);
        if (target == null && saved != null && "GET".equals(saved.getMethod())) {
            URI savedUri = URI.create(saved.getRedirectUrl());
            String path = savedUri.getRawPath();
            String context = request.getContextPath();
            if (!context.isEmpty() && path.startsWith(context + "/")) path = path.substring(context.length());
            target = shoppingPath(path + (savedUri.getRawQuery() == null ? "" : "?" + savedUri.getRawQuery()));
        }
        return hasRole(authentication, "ROLE_CUSTOMER") && shoppingPath(target) != null
                ? target : home(authentication);
    }

    public String home(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) return "/admin/dashboard";
        if (hasRole(authentication, "ROLE_STORE_MANAGER")) return "/seller";
        if (hasRole(authentication, "ROLE_DELIVERY_STAFF")) return "/delivery";
        if (hasRole(authentication, "ROLE_CUSTOMER")) return "/home";
        return authentication.getAuthorities().isEmpty() ? "/customer/seller-applications" : "/access-denied";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        response.sendRedirect(request.getContextPath() + destination(authentication, request, response));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private String shoppingPath(String value) {
        if (value == null || value.length() > 4096 || !value.startsWith("/") || value.startsWith("//")
                || value.contains("\\") || value.chars().anyMatch(Character::isISOControl)) return null;
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || !path.equals(uri.normalize().getRawPath())) return null;
            // Only browser pages are destinations; never replay a mutation or send the browser off-site.
            return path.matches("/(home|cart|shops(/\\d+(/(reviews|offers))?)?|product-detail(/\\d+)?|customer/(home|profile|orders(/\\d+)?|checkout|addresses))")
                    ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
