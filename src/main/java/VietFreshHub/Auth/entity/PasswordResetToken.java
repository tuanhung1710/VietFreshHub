package VietFreshHub.Auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        schema = "dbo",
        name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_password_reset_tokens_token_hash",
                columnNames = "token_hash"
        )
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_token_id")
    private Long resetTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30)
    private TokenType tokenType = TokenType.PASSWORD_RESET;
    @Nationalized
    @Column(name = "token_hash", nullable = false, length = 500)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
