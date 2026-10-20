package VietFreshHub.loyalty;

import VietFreshHub.auth.entity.User;
import VietFreshHub.loyalty.entity.*;
import VietFreshHub.loyalty.repository.*;
import VietFreshHub.loyalty.services.LoyaltyServiceImpl;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoyaltyMoneyTest {
    private final LoyaltyAccountRepository accounts = mock(LoyaltyAccountRepository.class);
    private final LoyaltyTransactionRepository transactions = mock(LoyaltyTransactionRepository.class);
    private final LoyaltyRuleRepository rules = mock(LoyaltyRuleRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final LoyaltyServiceImpl service = new LoyaltyServiceImpl(accounts, transactions, rules, em);
    private final User customer = new User();
    private final Order order = new Order();
    private final LoyaltyRule rule = new LoyaltyRule();
    private LoyaltyAccount account;

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static void amount(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }

    @BeforeEach
    void setUp() {
        customer.setUserId(7L);
        order.setOrderId(42L);
        order.setCustomer(customer);
        order.setOrderCode("VF-42");
        order.setSubtotal(money("200000"));
        order.setDiscountTotal(money("20000"));
        order.setShippingFee(money("15000"));
        order.setGrandTotal(money("195000"));
        account = LoyaltyAccount.builder().user(customer).rewardBalance(money("50000"))
                .totalEarnedAmount(money("50000")).build();
        rule.setEarningEnabled(true);
        rule.setEarningPercent(money("1.00"));
        rule.setRedemptionEnabled(true);
        rule.setMinRedemptionAmount(money("1000"));
        rule.setMaxRedemptionPercent(20);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(accounts.findByUser_UserId(7L)).thenReturn(Optional.of(account));
        when(accounts.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(em.find(User.class, 7L)).thenReturn(customer);
        when(em.find(Order.class, 42L)).thenReturn(order);
    }

    @Test
    void previewIsOneToOneAndNeverChangesBalanceOrLedger() {
        var preview = service.previewRedemption(7L, money("10000"), money("180000"));
        amount("10000", preview.discountAmount());
        amount("40000", preview.balanceAfterAmount());
        amount("50000", account.getRewardBalance());
        verifyNoInteractions(transactions, em);
        verify(accounts, never()).save(any());
    }

    @Test
    void zeroDoesNotRequireEnabledRulesOrAnAccount() {
        when(accounts.findByUser_UserId(7L)).thenReturn(Optional.empty());
        amount("0", service.previewRedemption(7L, BigDecimal.ZERO, BigDecimal.ZERO).discountAmount());
        verifyNoInteractions(rules, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.1", "999", "50001", "36001", "10000000000000000"})
    void rejectsInvalidRedemption(String input) {
        assertThrows(IllegalArgumentException.class,
                () -> service.previewRedemption(7L, money(input), money("180000")));
        amount("50000", account.getRewardBalance());
        verifyNoInteractions(transactions);
    }

    @Test
    void minimumAndMaximumComeFromDatabase() {
        rule.setMinRedemptionAmount(money("2000"));
        rule.setMaxRedemptionPercent(10);
        assertThrows(IllegalArgumentException.class, () -> service.previewRedemption(7L, money("1000"), money("180000")));
        amount("18000", service.previewRedemption(7L, money("18000"), money("180000")).discountAmount());
        assertThrows(IllegalArgumentException.class, () -> service.previewRedemption(7L, money("18001"), money("180000")));
        rule.setRedemptionEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.previewRedemption(7L, money("2000"), money("180000")));
    }

    @Test
    void earnsOnePercentAfterCouponAndRewardExcludingShippingAndRoundsDown() {
        order.setStatus(Order.Status.COMPLETED);
        order.setPaymentStatus("PAID");
        order.setDiscountTotal(money("30000"));
        order.setGrandTotal(money("185000"));
        amount("1700", service.earnReward(42L));
        amount("51700", account.getRewardBalance());
        verify(transactions).saveAndFlush(argThat(t -> t.getAmount().compareTo(money("1700")) == 0
                && t.getTransactionType() == LoyaltyTransactionType.EARN));
        order.setSubtotal(money("200099"));
        rule.setEarningPercent(money("2.50"));
        amount("4252", service.earnReward(42L));
    }

    @Test
    void onlyCompletedAndPaidCanEarnAndDuplicateIsIgnored() {
        amount("0", service.earnReward(42L));
        order.setStatus(Order.Status.COMPLETED);
        amount("0", service.earnReward(42L));
        order.setStatus(Order.Status.PARTIALLY_COMPLETED);
        order.setPaymentStatus("PAID");
        amount("0", service.earnReward(42L));
        order.setStatus(Order.Status.COMPLETED);
        when(transactions.existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(7L, 42L, LoyaltyTransactionType.EARN)).thenReturn(true);
        amount("0", service.earnReward(42L));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redemptionAllocatesAcrossShopsAndRecordsNegativeMoney() {
        TypedQuery<Long> paymentCount = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(paymentCount);
        when(paymentCount.getSingleResult()).thenReturn(0L);
        TypedQuery<ShopOrder> children = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(ShopOrder.class))).thenReturn(children);
        var first = shop("66666.67", "10000", "5000");
        var second = shop("66666.67", "10000", "5000");
        var third = shop("66666.66", "0", "5000");
        when(children.getResultList()).thenReturn(List.of(first, second, third));
        var redeemed = service.redeemToOrder(7L, 42L, money("10000"));
        amount("10000", redeemed.discountAmount());
        amount("30000", first.getDiscountTotal().add(second.getDiscountTotal()).add(third.getDiscountTotal()));
        amount("185000", order.getGrandTotal());
        amount("185000", first.getTotalAmount().add(second.getTotalAmount()).add(third.getTotalAmount()));
        amount("40000", account.getRewardBalance());
        amount("10000", account.getTotalRedeemedAmount());
        verify(transactions).saveAndFlush(argThat(t -> t.getAmount().compareTo(money("-10000")) == 0));
        verify(em).refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        verify(accounts).findByUserIdForUpdate(7L);
        verify(children).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void rejectsAnotherOwnerAndDuplicateRedemption() {
        var other = new User(); other.setUserId(8L); order.setCustomer(other);
        assertThrows(IllegalArgumentException.class, () -> service.redeemToOrder(7L, 42L, money("1000")));
        order.setCustomer(customer);
        when(transactions.existsByLoyaltyAccount_User_UserIdAndOrder_OrderIdAndTransactionType(7L, 42L, LoyaltyTransactionType.REDEEM)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.redeemToOrder(7L, 42L, money("1000")));
        verify(transactions, never()).saveAndFlush(any());
        amount("50000", account.getRewardBalance());
    }

    private ShopOrder shop(String subtotal, String coupon, String shipping) {
        var shop = new ShopOrder(); shop.setOrder(order);
        shop.setSubtotal(money(subtotal)); shop.setDiscountTotal(money(coupon)); shop.setShippingFee(money(shipping));
        return shop;
    }
}
