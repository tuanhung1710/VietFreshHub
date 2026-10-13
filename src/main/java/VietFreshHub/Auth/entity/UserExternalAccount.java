package VietFreshHub.Auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        schema = "dbo",
        name = "user_external_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_user_external_accounts_provider_identity",
                        columnNames = {"provider", "provider_user_id"}
                ),
                @UniqueConstraint(
                        name = "UQ_user_external_accounts_user_provider",
                        columnNames = {"user_id", "provider"}
                )
        }
)
public class UserExternalAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_account_id")
    private Long externalAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initializeUtcTimestamps() {
        var now = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
        if (createdAt == null) createdAt = now;
    }
}
