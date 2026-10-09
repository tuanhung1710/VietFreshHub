package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.UserExternalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserExternalAccountRepository
        extends JpaRepository<UserExternalAccount, Long> {

    Optional<UserExternalAccount> findByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );

    @Query("""
            select externalAccount.user.userId as userId, externalAccount.provider as provider
            from UserExternalAccount externalAccount
            where externalAccount.user.userId in :userIds
            order by externalAccount.provider
            """)
    List<ProviderRow> findProviderRowsByUserIds(@Param("userIds") Collection<Long> userIds);

    interface ProviderRow {
        Long getUserId();
        String getProvider();
    }
}
