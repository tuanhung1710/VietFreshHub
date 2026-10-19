package VietFreshHub.loyalty.services;

import VietFreshHub.auth.entity.User;
import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import VietFreshHub.loyalty.entity.LoyaltyAccount;
import VietFreshHub.loyalty.entity.LoyaltyTransaction;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;
import VietFreshHub.loyalty.repository.LoyaltyAccountRepository;
import VietFreshHub.loyalty.repository.LoyaltyRuleRepository;
import VietFreshHub.loyalty.repository.LoyaltyTransactionRepository;
import VietFreshHub.order.entity.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
    @Override
    @Transactional
    public int earnPoints(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("ID đơn hàng không hợp lệ");
        }

        Order order = em.find(Order.class, orderId);

        if (order == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }

        Long customerId = order.getCustomer().getUserId();

        // Khóa User trước, thống nhất với luồng Checkout.
        // Đồng thời bảo vệ việc tạo tài khoản điểm lần đầu.
        User customer = em.find(User.class, customerId);

        em.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!customerId.equals(order.getCustomer().getUserId())) {
            throw new IllegalStateException(
                    "Khách hàng của đơn vừa thay đổi"
            );
        }

        // Chưa hoàn tất hoặc chưa nhận đủ tiền thì chưa tích điểm.
        if (order.getStatus() != Order.Status.COMPLETED
                || !"PAID".equals(order.getPaymentStatus())) {
            return 0;
        }

        // Chống cộng lặp, kể cả khi xử lý lại cùng một đơn.
        if (transactionRepository
                .existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
                        customerId,
                        orderId,
                        LoyaltyTransactionType.EARN
                )) {
            return 0;
        }

        var rule = ruleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình quy tắc điểm thưởng"
                ));

        if (!rule.isEarningEnabled()) {
            return 0;
        }

        BigDecimal earningAmount = rule.getEarningAmount();

        if (earningAmount == null || earningAmount.signum() <= 0) {
            throw new IllegalStateException(
                    "Mức tiền để tích một điểm không hợp lệ"
            );
        }

        // Tiền hàng sau giảm giá, không tính phí giao hàng.
        BigDecimal eligibleAmount = order.getSubtotal()
                .subtract(order.getDiscountTotal());

        if (eligibleAmount.signum() <= 0) {
            return 0;
        }

        int earnedPoints = eligibleAmount
                .divide(earningAmount, 0, RoundingMode.DOWN)
                .intValueExact();

        if (earnedPoints == 0) {
            return 0;
        }

        LoyaltyAccount account = accountRepository
                .findByUserIdForUpdate(customerId)
                .orElse(null);

        if (account == null) {
            account = LoyaltyAccount.builder()
                    .user(customer)
                    .build();

            account = accountRepository.saveAndFlush(account);
        } else {
            em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        }

        int newBalance = Math.addExact(
                account.getPointsBalance(),
                earnedPoints
        );

        int newTotalEarned = Math.addExact(
                account.getTotalEarned(),
                earnedPoints
        );

        account.setPointsBalance(newBalance);
        account.setTotalEarned(newTotalEarned);

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .order(order)
                .transactionType(LoyaltyTransactionType.EARN)
                .points(earnedPoints)
                .balanceAfter(newBalance)
                .note(
                        "Tích điểm từ đơn " + order.getOrderCode()
                                + "; mỗi "
                                + earningAmount.toPlainString()
                                + "đ được 1 điểm"
                )
                .build();

        transactionRepository.saveAndFlush(transaction);

        return earnedPoints;
    }

}
