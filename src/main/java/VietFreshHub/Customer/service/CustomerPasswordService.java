package VietFreshHub.customer.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.customer.dto.CustomerPasswordRequest;
import VietFreshHub.security.Oidc_User;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerPasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isGoogleLogin(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Oidc_User;
    }

    @Transactional
    public void changePassword(Authentication authentication, CustomerPasswordRequest request) {
        if (isGoogleLogin(authentication)) {
            throw new IllegalStateException("Bạn đăng nhập bằng Google nên không thể đổi mật khẩu tại VietFresh Hub.");
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));

        if (user.getPasswordHash() == null) {
            throw new IllegalStateException("Tài khoản này chưa có mật khẩu VietFresh Hub.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
