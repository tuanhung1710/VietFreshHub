package VietFreshHub.checkout;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.repository.CouponRepository;
import VietFreshHub.coupon.repository.CouponUsageRepository;
import VietFreshHub.coupon.services.CouponApplicationServiceImpl;
import VietFreshHub.promotion.entity.*;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuyNowCouponTest {
    @Test void buyNowCouponUsesSelectedQuantityWithoutReadingOrChangingTheCart() {
        var em = mock(EntityManager.class);
        var coupons = mock(CouponRepository.class);
        var usage = mock(CouponUsageRepository.class);
        var service = new CouponApplicationServiceImpl(coupons, usage, em);
        var shop = new Shop(); shop.setShopId(3L);
        var product = new CatalogProduct(); product.setProductId(5L); product.setShop(shop);
        var variant = new CatalogVariant(); variant.setVariantId(7L); variant.setProduct(product);
        variant.setPrice(new BigDecimal("45000"));
        when(em.find(CatalogVariant.class,7L)).thenReturn(variant);
        var promotion = new Promotion(); promotion.setPromotionId(2L); promotion.setShopId(3L);
        promotion.setStatus(PromotionStatus.ACTIVE); promotion.setDiscountType(DiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN); promotion.setMinimumOrderValue(new BigDecimal("80000"));
        var now = LocalDateTime.now(ZoneOffset.UTC);
        promotion.setStartAt(now.minusDays(1)); promotion.setEndAt(now.plusDays(1));
        var coupon = new Coupon(); coupon.setCouponId(1L); coupon.setCode("FRUIT10");
        coupon.setPromotion(promotion); coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartAt(now.minusDays(1)); coupon.setEndAt(now.plusDays(1));
        when(coupons.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(coupon));
        when(coupons.findByPromotion_ShopIdInOrderByCodeAsc(List.of(3L))).thenReturn(List.of(coupon));
        var scope = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(scope);
        when(scope.setParameter(anyString(),any())).thenReturn(scope);
        when(scope.getSingleResult()).thenReturn(1);
        assertTrue(service.getAvailableBuyNowCoupons(8L,7L,1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.previewBuyNow(8L,"FRUIT10",7L,1));
        var result = service.previewBuyNow(8L,"FRUIT10",7L,2);
        assertEquals(0,new BigDecimal("9000").compareTo(result.discountAmount()));
        assertEquals(1,service.getAvailableBuyNowCoupons(8L,7L,2).size());
        verify(em,never()).createQuery(anyString(),eq(VietFreshHub.cart.entity.CartItem.class));
        verify(em,never()).persist(any());
        verify(em,never()).flush();
    }
}
