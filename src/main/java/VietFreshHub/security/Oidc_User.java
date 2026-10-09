package VietFreshHub.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class Oidc_User implements OidcUser, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OidcUser delegate;
    private final String localEmail;
    private final Collection<? extends GrantedAuthority> authorities;

    public Oidc_User(
            OidcUser delegate,
            String localEmail,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.delegate = delegate;
        this.localEmail = localEmail;
        this.authorities = List.copyOf(authorities);
    }

    @Override
    public String getName() {
        return localEmail;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public <A> A getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }
}