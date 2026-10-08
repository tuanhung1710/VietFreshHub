package VietFreshHub.Auth.service;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRoleRepository;
import VietFreshHub.config.Oidc_User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class Oidc_UserService
        implements org.springframework.security.oauth2.client.userinfo.OAuth2UserService<OidcUserRequest, OidcUser> {

    private final GoogleAccountService googleAccountService;
    private final UserRoleRepository userRoleRepository;

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {
        OidcUser googleUser = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"google".equalsIgnoreCase(registrationId)) {
            return googleUser;
        }

        String subject = googleUser.getSubject();
        String email = googleUser.getClaimAsString("email");
        String fullName = googleUser.getClaimAsString("name");
        String picture = googleUser.getClaimAsString("picture");
        boolean emailVerified = Boolean.TRUE.equals(
                googleUser.getClaimAsBoolean("email_verified")
        );

        User localUser = googleAccountService.findOrCreateGoogleUser(
                subject,
                email,
                fullName,
                picture,
                emailVerified
        );

        List<String> roleNames = userRoleRepository
                .findRoleNamesByUserId(localUser.getUserId());

        if (roleNames.isEmpty()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_has_no_roles"),
                    "This VietFresh Hub account has no assigned role."
            );
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(googleUser.getAuthorities());
        roleNames.forEach(roleName ->
                authorities.add(new SimpleGrantedAuthority(roleName))
        );

        return new Oidc_User(
                googleUser,
                localUser.getEmail(),
                authorities
        );
    }
}
