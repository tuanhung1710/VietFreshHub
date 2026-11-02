package VietFreshHub.auth.service;

import VietFreshHub.auth.entity.PasswordResetToken;
import VietFreshHub.auth.entity.TokenType;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.PasswordResetTokenRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.mail.service.MailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LIFETIME_MINUTES = 15;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailTemplateService mailTemplateService;
    private final TransactionTemplate transactionTemplate;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            MailTemplateService mailTemplateService,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailTemplateService = mailTemplateService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void requestReset(String email, String resetBaseUrl) {
        PreparedReset prepared = transactionTemplate.execute(status -> prepareReset(email));
        if (prepared == null) {
            return;
        }

        String resetUrl = UriComponentsBuilder.fromUriString(resetBaseUrl)
                .queryParam("token", prepared.rawToken())
                .build()
                .encode()
                .toUriString();
        try {
            mailTemplateService.sendPasswordReset(
                    prepared.email(), prepared.fullName(), resetUrl, TOKEN_LIFETIME_MINUTES
            );
        } catch (RuntimeException exception) {
            log.warn("Không gửi được email đặt lại mật khẩu.");
            transactionTemplate.executeWithoutResult(status -> markTokenUsed(prepared.tokenId()));
        }
    }

    public boolean isTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return false;
        }
        LocalDateTime now = utcNow();
        return tokenRepository.findByTokenHashAndTokenType(
                        hashToken(rawToken), TokenType.PASSWORD_RESET
                )
                .filter(token -> token.getUsedAt() == null)
                .filter(token -> now.isBefore(token.getExpiresAt()))
                .isPresent();
    }

    public void resetPassword(String rawToken, String newPassword) {
        transactionTemplate.executeWithoutResult(status -> {
            if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
                throw invalidToken();
            }

            PasswordResetToken token = tokenRepository
                    .findByTokenHashAndTokenTypeForUpdate(
                            hashToken(rawToken), TokenType.PASSWORD_RESET
                    )
                    .orElseThrow(this::invalidToken);
            LocalDateTime now = utcNow();
            User user = token.getUser();
            if (token.getUsedAt() != null
                    || !now.isBefore(token.getExpiresAt())
                    || !ACTIVE_STATUS.equalsIgnoreCase(user.getStatus())
                    || user.getPasswordHash() == null) {
                throw invalidToken();
            }

            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            token.setUsedAt(now);
            tokenRepository.save(token);

            List<PasswordResetToken> otherTokens = tokenRepository
                    .findAllByUser_UserIdAndTokenTypeAndUsedAtIsNull(
                            user.getUserId(), TokenType.PASSWORD_RESET
                    );
            for (PasswordResetToken otherToken : otherTokens) {
                otherToken.setUsedAt(now);
            }
            if (!otherTokens.isEmpty()) {
                tokenRepository.saveAll(otherTokens);
            }
        });
    }

    private PreparedReset prepareReset(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null
                || !ACTIVE_STATUS.equalsIgnoreCase(user.getStatus())
                || user.getEmailVerifiedAt() == null
                || user.getPasswordHash() == null) {
            return null;
        }

        LocalDateTime now = utcNow();
        var recentToken = tokenRepository.findTopByUser_UserIdAndTokenTypeOrderByCreatedAtDesc(
                user.getUserId(), TokenType.PASSWORD_RESET
        );
        if (recentToken.isPresent()
                && recentToken.get().getCreatedAt() != null
                && recentToken.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            return null;
        }

        List<PasswordResetToken> previousTokens = tokenRepository
                .findAllByUser_UserIdAndTokenTypeAndUsedAtIsNull(
                        user.getUserId(), TokenType.PASSWORD_RESET
                );
        for (PasswordResetToken previousToken : previousTokens) {
            previousToken.setUsedAt(now);
        }
        if (!previousTokens.isEmpty()) {
            tokenRepository.saveAll(previousTokens);
        }

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenType(TokenType.PASSWORD_RESET);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setAttemptCount(0);
        resetToken.setExpiresAt(now.plusMinutes(TOKEN_LIFETIME_MINUTES));
        PasswordResetToken savedToken = tokenRepository.saveAndFlush(resetToken);

        return new PreparedReset(
                savedToken.getResetTokenId(), rawToken, user.getEmail(), user.getFullName()
        );
    }

    private void markTokenUsed(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(token -> {
            token.setUsedAt(utcNow());
            tokenRepository.save(token);
        });
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không hỗ trợ SHA-256 để xử lý token.", exception);
        }
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private record PreparedReset(Long tokenId, String rawToken, String email, String fullName) {}
}
