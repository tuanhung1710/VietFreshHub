package VietFreshHub.loyalty.repository;

import VietFreshHub.loyalty.entity.LoyaltyRule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyRuleRepository
        extends JpaRepository<LoyaltyRule, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rule
            FROM LoyaltyRule rule
            WHERE rule.ruleId = :ruleId
            """)
    Optional<LoyaltyRule> findForUpdate(
            @Param("ruleId") Long ruleId
    );
}