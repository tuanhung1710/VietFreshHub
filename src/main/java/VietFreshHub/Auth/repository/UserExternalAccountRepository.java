package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.UserExternalAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserExternalAccountRepository
        extends JpaRepository<UserExternalAccount, Long> {

    Optional<UserExternalAccount> findByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );
}