package VietFreshHub.Auth.service;

import VietFreshHub.Auth.dto.PendingRegistration;
import VietFreshHub.Auth.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public PendingRegistration startRegistration(RegisterRequest request) {
        PendingRegistration pending = new PendingRegistration();
        pending.setFullName(request.getFullName().trim());
        pending.setEmail(request.getEmail().trim());
        pending.setPhone(request.getPhone().trim());

        // Chỉ giữ password hash trong session, không giữ mật khẩu gốc.
        pending.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        sendNewOtp(pending);
        return pending;
    }

    public void resendOtp(PendingRegistration pending) {
        LocalDateTime now = utcNow();

        if (pending.getLastSentAt() != null
                && pending.getLastSentAt().plusSeconds(60).isAfter(now)) {
            throw new IllegalStateException(
                    "Vui lòng đợi 60 giây trước khi gửi lại mã."
            );
        }

        sendNewOtp(pending);
    }

    public boolean verifyOtp(PendingRegistration pending, String otp) {
        LocalDateTime now = utcNow();

        if (pending == null
                || pending.getOtpHash() == null
                || pending.getExpiresAt() == null
                || !now.isBefore(pending.getExpiresAt())
                || pending.getAttemptCount() >= MAX_ATTEMPTS) {
            return false;
        }

        boolean correct = otp != null
                && otp.matches("\\d{6}")
                && passwordEncoder.matches(otp, pending.getOtpHash());

        if (!correct) {
            pending.setAttemptCount(pending.getAttemptCount() + 1);
        }

        return correct;
    }

    private void sendNewOtp(PendingRegistration pending) {
        if (mailFrom == null || mailFrom.isBlank()
                || mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            throw new IllegalStateException(
                    "Chưa cấu hình email gửi OTP. Hãy thiết lập MAIL_USERNAME và MAIL_PASSWORD."
            );
        }

        String otp = String.format(
                Locale.ROOT,
                "%06d",
                SECURE_RANDOM.nextInt(1_000_000)
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(pending.getEmail());
        message.setSubject("Mã xác thực email - VietFresh Hub");
        message.setText(
                "Xin chào " + pending.getFullName() + ",\n\n"
                        + "Mã OTP của bạn là: " + otp + "\n"
                        + "Mã có hiệu lực trong 10 phút. Vui lòng không chia sẻ mã này."
        );

        // Chỉ cập nhật OTP trong session sau khi gửi mail thành công.
        mailSender.send(message);

        LocalDateTime now = utcNow();
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(now.plusMinutes(10));
        pending.setLastSentAt(now);
        pending.setAttemptCount(0);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
