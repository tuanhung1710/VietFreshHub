package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import VietFreshHub.loyalty.entity.LoyaltyAccount;
import VietFreshHub.loyalty.entity.LoyaltyTransaction;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;
import VietFreshHub.loyalty.repository.LoyaltyAccountRepository;
import VietFreshHub.loyalty.repository.LoyaltyRuleRepository;
import VietFreshHub.loyalty.repository.LoyaltyTransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyServiceImpl implements LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyRuleRepository ruleRepository;
    private final EntityManager em;

    @Override
    public LoyaltyBalanceResponse getBalance(Long customerId) {
        requireCustomerId(customerId);

        return accountRepository.findByUser_UserId(customerId)
                .map(this::toBalanceResponse)
                .orElseGet(() -> new LoyaltyBalanceResponse(
                        null,
                        customerId,
                        0,
                        0,
                        0,
                        null
                ));
    }

    @Override
    public Page<LoyaltyTransactionResponse> getTransactionHistory(
            Long customerId,
            Pageable pageable
    ) {
        requireCustomerId(customerId);

        return transactionRepository
                .findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                        customerId,
                        pageable
                )
                .map(this::toTransactionResponse);
    }

    private LoyaltyBalanceResponse toBalanceResponse(
            LoyaltyAccount account
    ) {
        return new LoyaltyBalanceResponse(
                account.getLoyaltyAccountId(),
                account.getUser().getUserId(),
                account.getPointsBalance(),
                account.getTotalEarned(),
                account.getTotalRedeemed(),
                account.getUpdatedAt()
        );
    }

    private LoyaltyTransactionResponse toTransactionResponse(
            LoyaltyTransaction transaction
    ) {
        var order = transaction.getOrder();

        return new LoyaltyTransactionResponse(
                transaction.getLoyaltyTransactionId(),
                order == null ? null : order.getOrderId(),
                order == null ? null : order.getOrderCode(),
                transaction.getTransactionType(),
                transaction.getPoints(),
                transaction.getBalanceAfter(),
                transaction.getNote(),
                transaction.getCreatedAt()
        );
    }

    private void requireCustomerId(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException(
                    "ID khách hàng không hợp lệ"
            );
        }
    }
}
