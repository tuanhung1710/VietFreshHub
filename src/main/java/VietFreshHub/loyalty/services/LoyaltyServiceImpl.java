package VietFreshHub.loyalty.services;

import VietFreshHub.auth.entity.User;
import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionResponse;
import VietFreshHub.loyalty.dto.LoyaltyTransactionResponse;
import VietFreshHub.loyalty.entity.LoyaltyAccount;
import VietFreshHub.loyalty.entity.LoyaltyRule;
import VietFreshHub.loyalty.entity.LoyaltyTransaction;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;
import VietFreshHub.loyalty.repository.LoyaltyAccountRepository;
import VietFreshHub.loyalty.repository.LoyaltyRuleRepository;
import VietFreshHub.loyalty.repository.LoyaltyTransactionRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
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
                account.getRewardBalance(),
                account.getTotalEarnedAmount(),
                account.getTotalRedeemedAmount(),
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
                transaction.getAmount(),
                transaction.getBalanceAfterAmount(),
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
    public BigDecimal earnReward(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("ID đơn hàng không hợp lệ");
        }

        Order order = em.find(Order.class, orderId);

        if (order == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }

        Long customerId = order.getCustomer().getUserId();

        // Khóa User trước, thống nhất với luồng Checkout.
        // Đồng thời bảo vệ việc tạo tài khoản tiền thưởng lần đầu.
        User customer = em.find(User.class, customerId);

        em.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!customerId.equals(order.getCustomer().getUserId())) {
            throw new IllegalStateException(
                    "Khách hàng của đơn vừa thay đổi"
            );
        }

        // Chưa hoàn tất hoặc chưa nhận đủ tiền thì chưa tích thưởng.
        if (order.getStatus() != Order.Status.COMPLETED
                || !"PAID".equals(order.getPaymentStatus())) {
            return BigDecimal.ZERO;
        }

        // Chống cộng lặp, kể cả khi xử lý lại cùng một đơn.
        if (transactionRepository
                .existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
                        customerId,
                        orderId,
                        LoyaltyTransactionType.EARN
                )) {
            return BigDecimal.ZERO;
        }

        var rule = ruleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình quy tắc tiền thưởng"
                ));

        if (!rule.isEarningEnabled()) {
            return BigDecimal.ZERO;
        }

        BigDecimal earningPercent = rule.getEarningPercent();

        if (earningPercent == null || earningPercent.signum() <= 0
                || earningPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException(
                    "Tỷ lệ tích thưởng phải lớn hơn 0 và không quá 100%"
            );
        }

        // Tiền hàng sau giảm giá, không tính phí giao hàng.
        BigDecimal eligibleAmount = order.getSubtotal()
                .subtract(order.getDiscountTotal());

        if (eligibleAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal earnedReward = eligibleAmount.multiply(earningPercent)
                .divide(new BigDecimal("100"), 0, RoundingMode.DOWN);

        if (earnedReward.signum() == 0) {
            return BigDecimal.ZERO;
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

        BigDecimal newBalance = account.getRewardBalance().add(earnedReward);

        BigDecimal newTotalEarnedAmount = account.getTotalEarnedAmount().add(earnedReward);

        account.setRewardBalance(newBalance);
        account.setTotalEarnedAmount(newTotalEarnedAmount);

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .order(order)
                .transactionType(LoyaltyTransactionType.EARN)
                .amount(earnedReward)
                .balanceAfterAmount(newBalance)
                .note(
                        "Tích thưởng từ đơn " + order.getOrderCode()
                                + "; tỷ lệ "
                                + earningPercent.toPlainString()
                                + "%"
                )
                .build();

        transactionRepository.saveAndFlush(transaction);

        return earnedReward;
    }

    @Override
    public LoyaltyRedemptionResponse previewRedemption(
            Long customerId,
            BigDecimal rewardAmountToUse,
            BigDecimal goodsAmountAfterCoupon
    ) {
        requireCustomerId(customerId);
        validateRewardAmountToUse(rewardAmountToUse);

        BigDecimal balance = accountRepository.findByUser_UserId(customerId)
                .map(LoyaltyAccount::getRewardBalance)
                .orElse(BigDecimal.ZERO);

        if (rewardAmountToUse.signum() == 0) {
            return new LoyaltyRedemptionResponse(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    balance
            );
        }

        LoyaltyRule rule = getRedemptionRule();

        return calculateRedemption(
                rewardAmountToUse,
                balance,
                goodsAmountAfterCoupon,
                rule
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyRedemptionResponse redeemToOrder(
            Long customerId,
            Long orderId,
            BigDecimal rewardAmountToUse
    ) {
        requireCustomerId(customerId);
        validateRewardAmountToUse(rewardAmountToUse);

        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("ID đơn hàng không hợp lệ");
        }

        // Đồng bộ đơn và mức giảm coupon vừa được Checkout ghi.
        em.flush();

        User customer = em.find(User.class, customerId);

        if (customer == null) {
            throw new IllegalArgumentException("Không tìm thấy khách hàng");
        }

        em.refresh(customer, LockModeType.PESSIMISTIC_WRITE);

        Order order = em.find(Order.class, orderId);

        if (order == null
                || !customerId.equals(order.getCustomer().getUserId())) {
            throw new IllegalArgumentException(
                    "Không tìm thấy đơn hàng của bạn"
            );
        }

        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!customerId.equals(order.getCustomer().getUserId())) {
            throw new IllegalArgumentException(
                    "Đơn hàng không thuộc tài khoản của bạn"
            );
        }

        if (order.getStatus() != Order.Status.PENDING
                || !"UNPAID".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException(
                    "Chỉ dùng tiền thưởng khi tạo đơn chưa thanh toán"
            );
        }

        if (transactionRepository
                .existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
                        customerId,
                        orderId,
                        LoyaltyTransactionType.REDEEM
                )) {
            throw new IllegalArgumentException(
                    "Đơn hàng đã được dùng tiền thưởng"
            );
        }

        Long paymentCount = em.createQuery("""
            SELECT COUNT(p)
            FROM Payment p
            WHERE p.order.orderId = :orderId
            """, Long.class)
                .setParameter("orderId", orderId)
                .getSingleResult();

        if (paymentCount > 0) {
            throw new IllegalArgumentException(
                    "Phải dùng tiền thưởng trước khi tạo thanh toán"
            );
        }

        LoyaltyAccount account = accountRepository
                .findByUserIdForUpdate(customerId)
                .orElse(null);

        if (account != null) {
            em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        }

        BigDecimal balance = account == null ? BigDecimal.ZERO : account.getRewardBalance();

        if (rewardAmountToUse.signum() == 0) {
            return new LoyaltyRedemptionResponse(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    balance
            );
        }

        if (account == null) {
            throw new IllegalArgumentException(
                    "Bạn chưa có tiền thưởng để sử dụng"
            );
        }

        LoyaltyRule rule = getRedemptionRule();

        BigDecimal goodsAmount = order.getSubtotal()
                .subtract(order.getDiscountTotal());

        LoyaltyRedemptionResponse response = calculateRedemption(
                rewardAmountToUse,
                balance,
                goodsAmount,
                rule
        );

        List<ShopOrder> shopOrders = em.createQuery("""
            SELECT s
            FROM ShopOrder s
            WHERE s.order.orderId = :orderId
            ORDER BY s.shopOrderId
            """, ShopOrder.class)
                .setParameter("orderId", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        if (shopOrders.isEmpty()) {
            throw new IllegalStateException("Đơn hàng chưa có đơn con");
        }

        List<BigDecimal> shopGoods = new ArrayList<>();

        BigDecimal subtotalSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal shippingSum = BigDecimal.ZERO;

        for (ShopOrder shopOrder : shopOrders) {
            if (!"PENDING".equals(shopOrder.getStatus())) {
                throw new IllegalArgumentException(
                        "Đơn con không còn ở trạng thái chờ xử lý"
                );
            }

            BigDecimal remaining = shopOrder.getSubtotal()
                    .subtract(shopOrder.getDiscountTotal());

            if (remaining.signum() < 0) {
                throw new IllegalStateException(
                        "Tiền giảm của đơn con vượt tiền hàng"
                );
            }

            shopGoods.add(remaining);
            subtotalSum = subtotalSum.add(shopOrder.getSubtotal());
            discountSum = discountSum.add(shopOrder.getDiscountTotal());
            shippingSum = shippingSum.add(shopOrder.getShippingFee());
        }

        if (subtotalSum.compareTo(order.getSubtotal()) != 0
                || discountSum.compareTo(order.getDiscountTotal()) != 0
                || shippingSum.compareTo(order.getShippingFee()) != 0) {
            throw new IllegalStateException(
                    "Tổng tiền đơn hàng không khớp các đơn con"
            );
        }

        List<BigDecimal> allocations = allocateDiscount(
                response.discountAmount(),
                shopGoods,
                goodsAmount
        );

        for (int i = 0; i < shopOrders.size(); i++) {
            ShopOrder shopOrder = shopOrders.get(i);

            shopOrder.setDiscountTotal(
                    shopOrder.getDiscountTotal().add(allocations.get(i))
            );

            shopOrder.setTotalAmount(
                    shopOrder.getSubtotal()
                            .subtract(shopOrder.getDiscountTotal())
                            .add(shopOrder.getShippingFee())
            );
        }

        order.setDiscountTotal(
                order.getDiscountTotal().add(response.discountAmount())
        );

        order.setGrandTotal(
                order.getSubtotal()
                        .subtract(order.getDiscountTotal())
                        .add(order.getShippingFee())
        );

        account.setRewardBalance(response.balanceAfterAmount());

        account.setTotalRedeemedAmount(
                account.getTotalRedeemedAmount().add(rewardAmountToUse)
        );

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .order(order)
                .transactionType(LoyaltyTransactionType.REDEEM)
                .amount(rewardAmountToUse.negate())
                .balanceAfterAmount(response.balanceAfterAmount())
                .note(
                        "Dùng tiền thưởng cho đơn " + order.getOrderCode()
                                + "; giảm "
                                + response.discountAmount().toPlainString()
                                + "đ"
                )
                .build();

        transactionRepository.saveAndFlush(transaction);

        return response;
    }

    private void validateRewardAmountToUse(BigDecimal rewardAmountToUse) {
        if (rewardAmountToUse == null || rewardAmountToUse.signum() < 0
                || rewardAmountToUse.stripTrailingZeros().scale() > 0
                || rewardAmountToUse.compareTo(new BigDecimal("9999999999999999")) > 0) {
            throw new IllegalArgumentException(
                    "Tiền thưởng sử dụng phải là số đồng nguyên từ 0 đến 9.999.999.999.999.999"
            );
        }
    }

    private LoyaltyRule getRedemptionRule() {
        LoyaltyRule rule = ruleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình sử dụng tiền thưởng"
                ));

        if (!rule.isRedemptionEnabled()) {
            throw new IllegalArgumentException(
                    "Chức năng dùng tiền thưởng hiện đang tắt"
            );
        }

        if (rule.getMinRedemptionAmount() == null
                || rule.getMinRedemptionAmount().signum() <= 0
                || rule.getMinRedemptionAmount().stripTrailingZeros().scale() > 0
                || rule.getMaxRedemptionPercent() == null
                || rule.getMaxRedemptionPercent() < 1
                || rule.getMaxRedemptionPercent() > 100) {
            throw new IllegalStateException(
                    "Cấu hình dùng tiền thưởng không hợp lệ"
            );
        }

        return rule;
    }

    private LoyaltyRedemptionResponse calculateRedemption(
            BigDecimal rewardAmountToUse,
            BigDecimal balance,
            BigDecimal goodsAmount,
            LoyaltyRule rule
    ) {
        if (rewardAmountToUse.compareTo(rule.getMinRedemptionAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Cần dùng tối thiểu "
                            + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN"))
                                    .format(rule.getMinRedemptionAmount())
                            + " ₫ mỗi lần"
            );
        }

        if (rewardAmountToUse.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                    "Số dư tiền thưởng không đủ"
            );
        }

        if (goodsAmount == null || goodsAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Đơn hàng không còn tiền hàng để giảm bằng tiền thưởng"
            );
        }

        BigDecimal discount = rewardAmountToUse;

        BigDecimal maximumDiscount = goodsAmount
                .multiply(BigDecimal.valueOf(
                        rule.getMaxRedemptionPercent()
                ))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        if (discount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Số tiền thưởng chưa tạo ra mức giảm hợp lệ"
            );
        }

        if (discount.compareTo(maximumDiscount) > 0) {
            throw new IllegalArgumentException(
                    "Tiền thưởng sử dụng vượt mức giảm tối đa "
                            + rule.getMaxRedemptionPercent()
                            + "% tiền hàng sau coupon"
            );
        }

        return new LoyaltyRedemptionResponse(
                rewardAmountToUse,
                discount,
                balance.subtract(rewardAmountToUse)
        );
    }

    private List<BigDecimal> allocateDiscount(
            BigDecimal discount,
            List<BigDecimal> shopGoods,
            BigDecimal totalGoods
    ) {
        List<BigDecimal> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;

        for (BigDecimal amount : shopGoods) {
            BigDecimal share = discount.multiply(amount)
                    .divide(totalGoods, 2, RoundingMode.DOWN);

            allocations.add(share);
            allocated = allocated.add(share);
        }

        // Chia phần lẻ còn lại theo thứ tự shopOrderId.
        BigDecimal remainder = discount.subtract(allocated);
        BigDecimal unit = new BigDecimal("0.01");

        for (int i = 0; i < allocations.size()
                && remainder.signum() > 0; i++) {

            BigDecimal newShare = allocations.get(i).add(unit);

            if (newShare.compareTo(shopGoods.get(i)) <= 0) {
                allocations.set(i, newShare);
                remainder = remainder.subtract(unit);
            }
        }

        if (remainder.signum() != 0) {
            throw new IllegalStateException(
                    "Không phân bổ được mức giảm tiền thưởng"
            );
        }

        return allocations;
    }
}
