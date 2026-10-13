package VietFreshHub.security;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/** Enforces account status and current database roles for sessions already logged in. */
public class AccountSessionGuardFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SecurityContextRepository securityContextRepository;

    public AccountSessionGuardFilter(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SecurityContextRepository securityContextRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.equals("/favicon.ico")
                || path.equals("/login")
                || path.equals("/register")
                || path.equals("/verify-email")
                || path.equals("/verify-email/resend")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            expireSession(request, response, "accountStatusChanged");
            return;
        }

        Set<String> currentAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .collect(Collectors.toSet());
        Set<String> databaseAuthorities = Set.copyOf(
                userRoleRepository.findRoleNamesByUserId(user.getUserId())
        );
        if (!currentAuthorities.equals(databaseAuthorities)) {
            expireSession(request, response, "accountRolesChanged");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void expireSession(
            HttpServletRequest request,
            HttpServletResponse response,
            String reason
    ) throws IOException {
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextRepository.saveContext(emptyContext, request, response);
        response.sendRedirect(request.getContextPath() + "/login?" + reason + "=true");
    }
}
