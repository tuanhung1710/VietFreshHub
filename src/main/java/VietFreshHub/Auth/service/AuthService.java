package VietFreshHub.Auth.service;

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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();
        String phone = request.getPhone().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException("email", "Email này đã được sử dụng");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new RegistrationException("phone", "Số điện thoại này đã được sử dụng");
        }

        Role customerRole = roleRepository.findByRoleName("ROLE_CUSTOMER")
                .orElseThrow(() ->
                        new IllegalStateException("Chưa có ROLE_CUSTOMER trong bảng roles"));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(phone);

        // Không lưu mật khẩu gốc.
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user.setStatus("ACTIVE");
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
        Optional<User> result =
                userRepository.findByEmailIgnoreCase(email);

        if (result.isEmpty()) {
            return null;
        }

        User user = result.get();

        if (user.getPasswordHash() == null) {
            return null;
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return null;
        }

        // So sánh mật khẩu người dùng nhập với BCrypt hash trong database
        boolean passwordCorrect =
                passwordEncoder.matches(rawPassword, user.getPasswordHash());

        if (!passwordCorrect) {
            return null;
        }

        // Lấy role của user
        List<GrantedAuthority> authorities = new ArrayList<>();

        for (UserRole userRole : user.getUserRoles()) {
            String roleName = userRole.getRole().getRoleName();
            authorities.add(new SimpleGrantedAuthority(roleName));
        }

        // Tạo thông tin đăng nhập sau khi kiểm tra thành công

        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                authorities
        );
    }
}