package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.PasswordResetToken;
import VietFreshHub.auth.entity.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    List<PasswordResetToken>
    findAllByUser_UserIdAndTokenTypeAndUsedAtIsNull(
            Long userId,
            TokenType tokenType
    );

    Optional<PasswordResetToken>
    findTopByUser_UserIdAndTokenTypeAndUsedAtIsNullOrderByCreatedAtDesc(
            Long userId,
            TokenType tokenType
    );

    Optional<PasswordResetToken>
    findTopByUser_UserIdAndTokenTypeOrderByCreatedAtDesc(
            Long userId,
            TokenType tokenType
    );

}
