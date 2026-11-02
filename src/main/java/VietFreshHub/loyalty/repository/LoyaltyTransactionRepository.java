package VietFreshHub.loyalty.repository;

import VietFreshHub.loyalty.entity.LoyaltyTransaction;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyTransactionRepository
        extends JpaRepository<LoyaltyTransaction, Long> {

    @EntityGraph(attributePaths = "order")
    Page<LoyaltyTransaction>
    findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
            Long userId,
            Pageable pageable
    );

    boolean existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
            Long userId,
            Long orderId,
            LoyaltyTransactionType transactionType
    );

    Optional<LoyaltyTransaction>
    findByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
            Long userId,
            Long orderId,
            LoyaltyTransactionType transactionType
    );
}