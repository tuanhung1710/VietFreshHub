package VietFreshHub.Auth.service;

import VietFreshHub.Auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentCustomerService {
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Long requireCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Vui lòng đăng nhập bằng tài khoản khách hàng.");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn được phép mua hàng."))
                .getUserId();
    }
}
