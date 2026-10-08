package VietFreshHub.Auth.service;

import VietFreshHub.Auth.entity.Role;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.entity.UserExternalAccount;
import VietFreshHub.Auth.entity.UserRole;
import VietFreshHub.Auth.entity.UserRoleId;
import VietFreshHub.Auth.repository.RoleRepository;
import VietFreshHub.Auth.repository.UserExternalAccountRepository;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.repository.UserRoleRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class GoogleAccountService {

    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";

    private final UserRepository userRepository;
    private final UserExternalAccountRepository externalAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public GoogleAccountService(
            UserRepository userRepository,
            UserExternalAccountRepository externalAccountRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.externalAccountRepository = externalAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional
    public User findOrCreateGoogleUser(
            String googleSubject,
            String googleEmail,
            String fullName,
            String avatarUrl,
            boolean emailVerified
    ) {
        if (googleSubject == null || googleSubject.isBlank()) {
            throw oauthError("invalid_google_identity", "Google did not return a user identifier.");
        }

        var linkedAccount = externalAccountRepository
                .findByProviderAndProviderUserId(GOOGLE_PROVIDER, googleSubject);

        if (linkedAccount.isPresent()) {
            User user = linkedAccount.get().getUser();
            if (!ACTIVE_STATUS.equalsIgnoreCase(user.getStatus())) {
                throw oauthError("account_not_active", "This VietFresh Hub account is not active.");
            }
            return user;
        }

        if (googleEmail == null || googleEmail.isBlank()) {
            throw oauthError("missing_email", "Google did not return an email address.");
        }

        if (!emailVerified) {
            throw oauthError("email_not_verified", "Google did not verify this email address.");
        }

        String normalizedEmail = googleEmail.trim().toLowerCase(Locale.ROOT);

        // Do not attach a new Google identity to an existing local account based only on email.
        // The existing account must be authenticated and linked through a separate explicit flow.
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw oauthError(
                    "account_link_required",
                    "An account with this email already exists. Sign in to that account and link Google first."
            );
        }

        Role customerRole = roleRepository.findByRoleName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_CUSTOMER is missing from the roles table."
                ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(fullName == null || fullName.isBlank()
                ? normalizedEmail
                : fullName.trim());
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(null);
        user.setStatus(ACTIVE_STATUS);
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);

        UserRoleId userRoleId = new UserRoleId();
        userRoleId.setUserId(savedUser.getUserId());
        userRoleId.setRoleId(customerRole.getRoleId());

        UserRole userRole = new UserRole();
        userRole.setId(userRoleId);
        userRole.setUser(savedUser);
        userRole.setRole(customerRole);
        userRoleRepository.save(userRole);

        UserExternalAccount externalAccount = new UserExternalAccount();
        externalAccount.setUser(savedUser);
        externalAccount.setProvider(GOOGLE_PROVIDER);
        externalAccount.setProviderUserId(googleSubject);
        externalAccountRepository.save(externalAccount);

        return savedUser;
    }

    private OAuth2AuthenticationException oauthError(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), description);
    }
}