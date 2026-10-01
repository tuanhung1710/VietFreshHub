package VietFreshHub.Auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        schema = "dbo",
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_users_email",
                columnNames = "email"
        )
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String email;

    @Nationalized
    @Column(name = "password_hash", length = 500)
    private String passwordHash;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Nationalized
    @Column(length = 30)
    private String phone;

    @Nationalized
    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRole> userRoles = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserExternalAccount> externalAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<PasswordResetToken> passwordResetTokens = new ArrayList<>();
}
