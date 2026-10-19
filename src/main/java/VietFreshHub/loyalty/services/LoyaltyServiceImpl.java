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

    @Override
    public LoyaltyRedemptionResponse previewRedemption(
            Long customerId,
            Integer pointsToUse,
            BigDecimal goodsAmountAfterCoupon
    ) {
        requireCustomerId(customerId);
        validatePointsToUse(pointsToUse);

        int balance = accountRepository.findByUser_UserId(customerId)
                .map(LoyaltyAccount::getPointsBalance)
                .orElse(0);

        if (pointsToUse == 0) {
            return new LoyaltyRedemptionResponse(
                    0,
                    BigDecimal.ZERO,
                    balance
            );
        }

        LoyaltyRule rule = getRedemptionRule();

        return calculateRedemption(
                pointsToUse,
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
            Integer pointsToUse
    ) {
        requireCustomerId(customerId);
        validatePointsToUse(pointsToUse);

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
                    "Chỉ dùng điểm khi tạo đơn chưa thanh toán"
            );
        }

        if (transactionRepository
                .existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(
                        customerId,
                        orderId,
                        LoyaltyTransactionType.REDEEM
                )) {
            throw new IllegalArgumentException(
                    "Đơn hàng đã được đổi điểm"
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
                    "Phải đổi điểm trước khi tạo thanh toán"
            );
        }

        LoyaltyAccount account = accountRepository
                .findByUserIdForUpdate(customerId)
                .orElse(null);

        if (account != null) {
            em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        }

        int balance = account == null ? 0 : account.getPointsBalance();

        if (pointsToUse == 0) {
            return new LoyaltyRedemptionResponse(
                    0,
                    BigDecimal.ZERO,
                    balance
            );
        }

        if (account == null) {
            throw new IllegalArgumentException(
                    "Bạn chưa có điểm thưởng để sử dụng"
            );
        }

        LoyaltyRule rule = getRedemptionRule();

        BigDecimal goodsAmount = order.getSubtotal()
                .subtract(order.getDiscountTotal());

        LoyaltyRedemptionResponse response = calculateRedemption(
                pointsToUse,
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

        account.setPointsBalance(response.balanceAfter());

        account.setTotalRedeemed(
                Math.addExact(account.getTotalRedeemed(), pointsToUse)
        );

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .order(order)
                .transactionType(LoyaltyTransactionType.REDEEM)
                .points(-pointsToUse)
                .balanceAfter(response.balanceAfter())
                .note(
                        "Dùng điểm cho đơn " + order.getOrderCode()
                                + "; giảm "
                                + response.discountAmount().toPlainString()
                                + "đ"
                )
                .build();

        transactionRepository.saveAndFlush(transaction);

        return response;
    }

    private void validatePointsToUse(Integer pointsToUse) {
        if (pointsToUse == null || pointsToUse < 0) {
            throw new IllegalArgumentException(
                    "Số điểm sử dụng phải từ 0 trở lên"
            );
        }
    }

    private LoyaltyRule getRedemptionRule() {
        LoyaltyRule rule = ruleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình đổi điểm thưởng"
                ));

        if (!rule.isRedemptionEnabled()) {
            throw new IllegalArgumentException(
                    "Chức năng đổi điểm hiện đang tắt"
            );
        }

        if (rule.getRedemptionValue() == null
                || rule.getRedemptionValue().signum() <= 0
                || rule.getMinRedemptionPoints() == null
                || rule.getMinRedemptionPoints() <= 0
                || rule.getMaxRedemptionPercent() == null
                || rule.getMaxRedemptionPercent() < 1
                || rule.getMaxRedemptionPercent() > 100) {
            throw new IllegalStateException(
                    "Cấu hình đổi điểm không hợp lệ"
            );
        }

        return rule;
    }

    private LoyaltyRedemptionResponse calculateRedemption(
            int pointsToUse,
            int balance,
            BigDecimal goodsAmount,
            LoyaltyRule rule
    ) {
        if (pointsToUse < rule.getMinRedemptionPoints()) {
            throw new IllegalArgumentException(
                    "Cần dùng tối thiểu "
                            + rule.getMinRedemptionPoints()
                            + " điểm mỗi lần"
            );
        }

        if (pointsToUse > balance) {
            throw new IllegalArgumentException(
                    "Số dư điểm thưởng không đủ"
            );
        }

        if (goodsAmount == null || goodsAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Đơn hàng không còn tiền hàng để giảm bằng điểm"
            );
        }

        BigDecimal discount = rule.getRedemptionValue()
                .multiply(BigDecimal.valueOf(pointsToUse))
                .setScale(2, RoundingMode.DOWN);

        BigDecimal maximumDiscount = goodsAmount
                .multiply(BigDecimal.valueOf(
                        rule.getMaxRedemptionPercent()
                ))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        if (discount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Số điểm chưa tạo ra mức giảm hợp lệ"
            );
        }

        if (discount.compareTo(maximumDiscount) > 0) {
            throw new IllegalArgumentException(
                    "Điểm sử dụng vượt mức giảm tối đa "
                            + rule.getMaxRedemptionPercent()
                            + "% tiền hàng sau coupon"
            );
        }

        return new LoyaltyRedemptionResponse(
                pointsToUse,
                discount,
                balance - pointsToUse
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
                    "Không phân bổ được mức giảm điểm thưởng"
            );
        }

        return allocations;
    }
}