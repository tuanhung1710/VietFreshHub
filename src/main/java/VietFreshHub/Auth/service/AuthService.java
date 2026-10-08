package VietFreshHub.Auth.service;

import VietFreshHub.Auth.dto.PendingRegistration;
import VietFreshHub.Auth.dto.RegisterRequest;
import VietFreshHub.Auth.dto.RegisterResponse;
import VietFreshHub.Auth.entity.Role;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.entity.UserRole;
import VietFreshHub.Auth.entity.UserRoleId;
import VietFreshHub.Auth.exception.RegistrationException;
import VietFreshHub.Auth.repository.RoleRepository;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public void validateRegistration(RegisterRequest request) {
        String email = request.getEmail().trim();
        String phone = request.getPhone().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException("email", "Email này đã được sử dụng");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new RegistrationException("phone", "Số điện thoại này đã được sử dụng");
        }

        if (roleRepository.findByRoleName("ROLE_CUSTOMER").isEmpty()) {
            throw new IllegalStateException(
                    "Chưa có ROLE_CUSTOMER trong bảng roles"
            );
        }
    }

    @Transactional
    public RegisterResponse registerVerified(PendingRegistration pending) {
        String email = pending.getEmail().trim();
        String phone = pending.getPhone().trim();

        // Kiểm tra lại trước khi lưu, phòng trường hợp email/điện thoại
        // được đăng ký trong lúc người dùng đang nhập OTP.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException("email", "Email này đã được sử dụng");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new RegistrationException("phone", "Số điện thoại này đã được sử dụng");
        }

        Role customerRole = roleRepository.findByRoleName("ROLE_CUSTOMER")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Chưa có ROLE_CUSTOMER trong bảng roles"
                        ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        User user = new User();
        user.setFullName(pending.getFullName());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(pending.getPasswordHash());
        user.setStatus("ACTIVE");
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);

        UserRoleId id = new UserRoleId();
        id.setUserId(savedUser.getUserId());
        id.setRoleId(customerRole.getRoleId());

        UserRole userRole = new UserRole();
        userRole.setId(id);
        userRole.setUser(savedUser);
        userRole.setRole(customerRole);
        userRoleRepository.save(userRole);

        return new RegisterResponse(
                savedUser.getUserId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                "Đăng ký tài khoản thành công"
        );
    }

    @Transactional
    public Authentication login(String email, String rawPassword) {
        if (email == null || email.isBlank() || rawPassword == null) {
            return null;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElse(null);

        if (user == null
                || user.getPasswordHash() == null
//                || user.getEmailVerifiedAt() == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return null;
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return null;
        }

        return createAuthentication(user);
    }

    /**
     * Chỉ gọi sau khi OTP hợp lệ và registerVerified đã tạo user.
     */
    @Transactional(readOnly = true)
    public Authentication loginAfterEmailVerification(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElse(null);

        if (user == null
                || user.getEmailVerifiedAt() == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return null;
        }

        return createAuthentication(user);
    }

    private Authentication createAuthentication(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        List<UserRole> userRoles =
                userRoleRepository.findAllByUser_UserId(user.getUserId());

        for (UserRole userRole : userRoles) {
            authorities.add(
                    new SimpleGrantedAuthority(
                            userRole.getRole().getRoleName()
                    )
            );
        }

        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                authorities
        );
    }
}