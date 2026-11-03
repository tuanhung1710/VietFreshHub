package VietFreshHub.checkout;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponUsage;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.repository.CouponRepository;
import VietFreshHub.coupon.repository.CouponUsageRepository;
import VietFreshHub.coupon.services.CouponApplicationServiceImpl;
import VietFreshHub.promotion.entity.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.auth.entity.User;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
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
        coupon.setEndAt(now.minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.previewBuyNow(8L,"FRUIT10",7L,2));
        coupon.setEndAt(now.plusDays(1));coupon.setUsageLimit(1);
        when(usage.countByCoupon_CouponId(1L)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.previewBuyNow(8L,"FRUIT10",7L,2));
        coupon.setUsageLimit(null);promotion.setShopId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.previewBuyNow(8L,"FRUIT10",7L,2));
        verify(em,never()).createQuery(anyString(),eq(VietFreshHub.cart.entity.CartItem.class));
        verify(em,never()).persist(any());
        verify(em,never()).flush();
    }

    @Test void rejectsTwoCouponsFromTheSameShopBeforeApplyingEither() {
        var em = mock(EntityManager.class);
        var coupons = mock(CouponRepository.class);
        var service = new CouponApplicationServiceImpl(coupons, mock(CouponUsageRepository.class), em);
        var promotion = new Promotion(); promotion.setShopId(3L);
        var first = new Coupon(); first.setCode("ONE"); first.setPromotion(promotion);
        var second = new Coupon(); second.setCode("TWO"); second.setPromotion(promotion);
        when(coupons.findByCodeIgnoreCase("ONE")).thenReturn(Optional.of(first));
        when(coupons.findByCodeIgnoreCase("TWO")).thenReturn(Optional.of(second));
        var error = assertThrows(IllegalArgumentException.class, () -> service.applyToOrder(8L, 11L, List.of("ONE", "TWO")));
        assertTrue(error.getMessage().contains("một coupon"));
        verify(em, never()).flush();
    }

    @Test void appliesOneCouponPerShopAndRecalculatesOrderTotals() {
        var em = mock(EntityManager.class);var coupons = mock(CouponRepository.class);var usage = mock(CouponUsageRepository.class);
        var service = new CouponApplicationServiceImpl(coupons, usage, em);
        var buyer = new User();buyer.setUserId(8L);
        var order = new VietFreshHub.order.entity.Order();order.setOrderId(11L);order.setCustomer(buyer);order.setSubtotal(new BigDecimal("30000"));order.setShippingFee(new BigDecimal("3000"));
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var shopOrders = new ArrayList<ShopOrder>();var itemSets = new HashMap<Long,List<OrderItem>>();
        for (long id=1;id<=2;id++) {
            var shop = new Shop();shop.setShopId(id);
            var product = new CatalogProduct();product.setShop(shop);
            var variant = new CatalogVariant();variant.setProduct(product);
            var item = new OrderItem();item.setVariant(variant);item.setUnitPrice(new BigDecimal("15000"));item.setQuantity(1);item.setDiscountAmount(BigDecimal.ZERO);
            var shopOrder = new ShopOrder();shopOrder.setShopOrderId(id);shopOrder.setOrder(order);shopOrder.setShopId(id);shopOrder.setSubtotal(new BigDecimal("15000"));shopOrder.setShippingFee(new BigDecimal("1500"));
            itemSets.put(id,List.of(item));shopOrders.add(shopOrder);
            var promotion=new Promotion();promotion.setPromotionId(id);promotion.setShopId(id);promotion.setStatus(PromotionStatus.ACTIVE);promotion.setStartAt(now.minusDays(1));promotion.setEndAt(now.plusDays(1));promotion.setDiscountType(DiscountType.FIXED);promotion.setDiscountValue(new BigDecimal("1000"));promotion.setMinimumOrderValue(BigDecimal.ZERO);
            var coupon=new Coupon();coupon.setCouponId(id);coupon.setCode("SHOP"+id);coupon.setPromotion(promotion);coupon.setStatus(CouponStatus.ACTIVE);coupon.setStartAt(now.minusDays(1));coupon.setEndAt(now.plusDays(1));coupon.setMinimumOrderValue(BigDecimal.ZERO);
            when(coupons.findByCodeIgnoreCase(coupon.getCode())).thenReturn(Optional.of(coupon));when(coupons.findByCodeForUpdate(coupon.getCode())).thenReturn(Optional.of(coupon));
        }
        @SuppressWarnings("unchecked") TypedQuery<Long> count=mock(TypedQuery.class);when(count.setParameter(anyString(),any())).thenReturn(count);when(count.getSingleResult()).thenReturn(0L);
        @SuppressWarnings("unchecked") TypedQuery<ShopOrder> shopQuery=mock(TypedQuery.class);when(shopQuery.setParameter(anyString(),any())).thenReturn(shopQuery);when(shopQuery.getResultList()).thenReturn(shopOrders);
        @SuppressWarnings("unchecked") TypedQuery<OrderItem> itemQuery=mock(TypedQuery.class);when(itemQuery.setParameter(anyString(),any())).thenAnswer(call->itemQuery);
        when(itemQuery.getResultList()).thenAnswer(call->{Long id=(Long) mockingDetails(itemQuery).getInvocations().stream().filter(i->i.getMethod().getName().equals("setParameter")).reduce((a,b)->b).orElseThrow().getArgument(1);return itemSets.get(id);});
        when(em.createQuery(contains("COUNT(p)"),eq(Long.class))).thenReturn(count);when(em.createQuery(contains("SELECT s"),eq(ShopOrder.class))).thenReturn(shopQuery);when(em.createQuery(contains("SELECT i"),eq(OrderItem.class))).thenReturn(itemQuery);
        when(em.find(VietFreshHub.order.entity.Order.class,11L)).thenReturn(order);
        Query scope=mock(Query.class),insert=mock(Query.class);when(scope.setParameter(anyString(),any())).thenReturn(scope);when(scope.getSingleResult()).thenReturn(1);when(insert.setParameter(anyString(),any())).thenReturn(insert);when(insert.executeUpdate()).thenReturn(1);
        when(em.createNativeQuery(startsWith("SELECT CASE"))).thenReturn(scope);when(em.createNativeQuery(startsWith("INSERT INTO dbo.order_discounts"))).thenReturn(insert);
        when(usage.saveAndFlush(any(CouponUsage.class))).thenAnswer(call->call.getArgument(0));
        var applications=service.applyToOrder(8L,11L,List.of("SHOP1","SHOP2"));
        assertEquals(2,applications.size());assertEquals(new BigDecimal("2000"),order.getDiscountTotal());assertEquals(new BigDecimal("31000"),order.getGrandTotal());
        assertEquals(new BigDecimal("1000"),shopOrders.get(0).getDiscountTotal());assertEquals(new BigDecimal("15500"),shopOrders.get(0).getTotalAmount());
        verify(usage,times(2)).saveAndFlush(any(CouponUsage.class));verify(insert,times(2)).executeUpdate();
    }
}
