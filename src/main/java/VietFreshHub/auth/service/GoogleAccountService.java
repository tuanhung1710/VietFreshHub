package VietFreshHub.auth.service;

import VietFreshHub.auth.entity.Role;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.entity.UserExternalAccount;
import VietFreshHub.auth.entity.UserRole;
import VietFreshHub.auth.entity.UserRoleId;
import VietFreshHub.auth.repository.RoleRepository;
import VietFreshHub.auth.repository.UserExternalAccountRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
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

        var existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!ACTIVE_STATUS.equalsIgnoreCase(user.getStatus())) {
                throw oauthError("account_not_active", "This VietFresh Hub account is not active.");
            }
            if (user.getEmailVerifiedAt() == null) {
                throw oauthError(
                        "email_not_verified",
                        "Verify this VietFresh Hub email address before signing in with Google."
                );
            }
            if (externalAccountRepository.existsByUser_UserIdAndProvider(
                    user.getUserId(), GOOGLE_PROVIDER)) {
                throw oauthError(
                        "google_account_already_linked",
                        "This VietFresh Hub account is already linked to another Google account."
                );
            }

            linkGoogleAccount(user, googleSubject);
            return user;
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

        linkGoogleAccount(savedUser, googleSubject);

        return savedUser;
    }

    private void linkGoogleAccount(User user, String googleSubject) {
        UserExternalAccount externalAccount = new UserExternalAccount();
        externalAccount.setUser(user);
        externalAccount.setProvider(GOOGLE_PROVIDER);
        externalAccount.setProviderUserId(googleSubject);
        externalAccountRepository.save(externalAccount);
    }

    private OAuth2AuthenticationException oauthError(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), description);
    }
}
