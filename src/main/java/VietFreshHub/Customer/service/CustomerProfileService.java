package VietFreshHub.customer.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.customer.dto.CustomerProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerProfileService {

    private final UserRepository userRepository;

    public CustomerProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public CustomerProfileRequest getProfile(String authenticatedEmail) {
        User user = findCurrentUser(authenticatedEmail);
        CustomerProfileRequest profile = new CustomerProfileRequest();
        profile.setEmail(user.getEmail());
        profile.setFullName(user.getFullName());
        profile.setPhone(user.getPhone());
        return profile;
    }

    @Transactional
    public void updateProfile(String authenticatedEmail, CustomerProfileRequest request) {
        User user = findCurrentUser(authenticatedEmail);
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        if (fullName.isEmpty() || fullName.length() > 150) {
            throw new IllegalArgumentException("Họ và tên không hợp lệ.");
        }

        String phone = request.getPhone() == null || request.getPhone().isBlank()
                ? null
                : request.getPhone().trim();
        if (phone != null && phone.length() > 30) {
            throw new IllegalArgumentException("Số điện thoại tối đa 30 ký tự.");
        }
        if (phone != null && userRepository.existsByPhoneAndUserIdNot(phone, user.getUserId())) {
            throw new IllegalArgumentException("Số điện thoại này đã được tài khoản khác sử dụng.");
        }

        user.setFullName(fullName);
        user.setPhone(phone);
        userRepository.save(user);
    }

    private User findCurrentUser(String authenticatedEmail) {
        return userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));
    }
}
