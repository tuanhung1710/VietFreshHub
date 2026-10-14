package VietFreshHub.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MailTemplateService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String mailFrom;

    public void sendOtp(String recipientEmail, String recipientName, String otp) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("recipientName", recipientName);
        context.setVariable("otp", otp);

        send(
                recipientEmail,
                "Mã xác thực email - VietFresh Hub",
                "mail/otp-verification",
                context,
                "Xin chào " + recipientName + ",\n\n"
                        + "Mã OTP đăng ký VietFresh Hub của bạn là: " + otp + "\n"
                        + "Mã có hiệu lực trong 10 phút. Vui lòng không chia sẻ mã này."
        );
    }

    public void sendPasswordReset(
            String recipientEmail,
            String recipientName,
            String resetUrl,
            int expiresInMinutes
    ) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("recipientName", recipientName);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiresInMinutes", expiresInMinutes);

        send(
                recipientEmail,
                "Đặt lại mật khẩu VietFresh Hub",
                "mail/password-reset",
                context,
                "Xin chào " + recipientName + ",\n\n"
                        + "Bạn vừa yêu cầu đặt lại mật khẩu VietFresh Hub. Mở liên kết sau để tạo mật khẩu mới: "
                        + resetUrl + "\n\n"
                        + "Liên kết có hiệu lực trong " + expiresInMinutes
                        + " phút và chỉ sử dụng được một lần. Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email."
        );
    }

    public void sendSellerApplicationApproved(
            String recipientEmail,
            String recipientName,
            String businessName
    ) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("recipientName", recipientName);
        context.setVariable("businessName", businessName);

        send(
                recipientEmail,
                "Hồ sơ đăng ký bán hàng đã được duyệt - VietFresh Hub",
                "mail/seller-application-approved",
                context,
                "Xin chào " + recipientName + ",\n\n"
                        + "Hồ sơ đăng ký bán hàng cho " + businessName + " đã được phê duyệt.\n"
                        + "Tài khoản của bạn đã được cấp quyền Store Manager trên VietFresh Hub."
        );
    }

    private void send(
            String recipientEmail,
            String subject,
            String templateName,
            Context context,
            String plainText
    ) {
        String html = templateEngine.process(templateName, context);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Không gửi được email VietFresh Hub.", exception);
        }
    }
}
