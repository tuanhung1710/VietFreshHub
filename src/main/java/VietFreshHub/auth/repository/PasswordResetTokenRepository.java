package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.PasswordResetToken;
import VietFreshHub.auth.entity.TokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndTokenType(
            String tokenHash,
            TokenType tokenType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash and token.tokenType = :tokenType")
    Optional<PasswordResetToken> findByTokenHashAndTokenTypeForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("tokenType") TokenType tokenType
    );

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
