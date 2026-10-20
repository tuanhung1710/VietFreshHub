package VietFreshHub.coupon;

import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.repository.CouponRepository;
import VietFreshHub.coupon.repository.CouponUsageRepository;
import VietFreshHub.coupon.services.CouponApplicationServiceImpl;
import VietFreshHub.promotion.entity.DiscountType;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvailableCheckoutCouponsTest {
    private final CouponRepository coupons = mock(CouponRepository.class);
    private final CouponUsageRepository usages = mock(CouponUsageRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final CouponApplicationServiceImpl service = new CouponApplicationServiceImpl(coupons, usages, em);

    private Coupon coupon(long id) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var promotion = Promotion.builder().promotionId(id).shopId(30L)
                .status(PromotionStatus.ACTIVE).startAt(now.minusDays(1)).endAt(now.plusDays(1))
                .discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();
        return Coupon.builder().couponId(id).code("SAVE" + id).promotion(promotion)
                .status(CouponStatus.ACTIVE).startAt(now.minusDays(1)).endAt(now.plusDays(1)).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsOnlyUsableCouponsFromSelectedShopsWithoutRecordingUsage() {
        TypedQuery<CartItem> items = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(CartItem.class))).thenReturn(items);
        var item = mock(CartItem.class, RETURNS_DEEP_STUBS);
        when(item.getVariant().getProduct().getShop().getShopId()).thenReturn(30L);
        when(item.getVariant().getPrice()).thenReturn(new BigDecimal("100000"));
        when(item.getQuantity()).thenReturn(1);
        when(items.getResultList()).thenReturn(List.of(item));
        Query scope = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery(anyString())).thenReturn(scope);
        when(scope.getSingleResult()).thenReturn(1);

        var active = coupon(1L);
        var inactive = coupon(2L);
        inactive.setStatus(CouponStatus.INACTIVE);
        var expired = coupon(3L);
        expired.setEndAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        var exhausted = coupon(4L);
        exhausted.setUsageLimit(1);
        when(usages.countByCoupon_CouponId(4L)).thenReturn(1L);
        var minimum = coupon(5L);
        minimum.setMinimumOrderValue(new BigDecimal("200000"));
        var used = coupon(6L);
        used.setPerUserLimit(1);
        when(usages.countByCoupon_CouponIdAndUser_UserId(6L, 7L)).thenReturn(1L);
        when(coupons.findByPromotion_ShopIdInOrderByCodeAsc(List.of(30L)))
                .thenReturn(List.of(active, inactive, expired, exhausted, minimum, used));

        var available = service.getAvailableCoupons(7L, List.of(20L));

        assertEquals(1, available.size());
        assertEquals("SAVE1", available.getFirst().code());
        assertEquals(new BigDecimal("10000"), available.getFirst().discountAmount());
        verify(items).setParameter("customerId", 7L);
        verify(items).setParameter("itemIds", java.util.Set.of(20L));
        verify(coupons).findByPromotion_ShopIdInOrderByCodeAsc(List.of(30L));
        verify(usages, never()).save(any());
        verify(em, never()).persist(any());
    }

    @Test
    void emptySelectionHasNoCouponsAndInvalidCustomerIsRejected() {
        assertTrue(service.getAvailableCoupons(7L, List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableCoupons(null, List.of(20L)));
        verifyNoInteractions(coupons, usages, em);
    }
}
