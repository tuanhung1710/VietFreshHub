package VietFreshHub.loyalty.repository;

import VietFreshHub.loyalty.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyAccountRepository
        extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByUser_UserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM LoyaltyAccount a
            WHERE a.user.userId = :userId
            """)
    Optional<LoyaltyAccount> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}