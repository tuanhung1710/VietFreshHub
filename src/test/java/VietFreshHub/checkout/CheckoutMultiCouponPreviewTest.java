package VietFreshHub.checkout;

import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.checkout.controller.CheckoutController;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import VietFreshHub.coupon.dto.CouponApplicationResponse;
import VietFreshHub.coupon.services.CouponApplicationService;
import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.services.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutMultiCouponPreviewTest {
    @Test void previewKeepsValidCouponAndShowsTheOtherCodesError() throws Exception {
        var coupons = mock(CouponApplicationService.class);
        var loyalty = mock(LoyaltyService.class);
        var carts = mock(VietFreshHub.cart.service.CartService.class);
        var controller = new CheckoutController(
                mock(VietFreshHub.auth.service.CurrentCustomerService.class),
                mock(VietFreshHub.checkout.service.CheckoutService.class),
                mock(VietFreshHub.checkout.service.CheckoutShippingQuoteService.class),
                new CheckoutDraftStore(), mock(VietFreshHub.checkout.service.CheckoutAddressService.class),
                mock(VietFreshHub.customer.service.CustomerAddressService.class), mock(VietFreshHub.vietmap.service.VietMapService.class),
                carts, coupons, loyalty, mock(VietFreshHub.payment.vnpay.VnpayPaymentService.class));
        var item = CartItemDto.builder().cartItemId(41L).variantId(9L).shopId(3L).shopName("Shop").quantity(1)
                .unitPriceSnapshot(new BigDecimal("10000")).itemSubtotal(new BigDecimal("10000")).build();
        var cart = CartDto.builder().cartId(7L).status("ACTIVE").items(List.of(item)).totalAmount(new BigDecimal("10000")).build();
        var preview = new CheckoutPreview(cart, List.of(), 2L, Map.of(), new BigDecimal("3000"), new BigDecimal("13000"),
                true, true, "fingerprint", null, false);
        var form = new CheckoutRequest();form.setCouponCodes(List.of("GOOD", "BAD"));
        var valid = new CouponApplicationResponse(1L, "GOOD", 2L, 3L, new BigDecimal("10000"), new BigDecimal("1000"), new BigDecimal("9000"));
        when(carts.countActiveUnits(7L)).thenReturn(1L);
        when(coupons.getAvailableCoupons(eq(7L), anyList())).thenReturn(List.of(valid));
        when(coupons.preview(eq(7L), eq("GOOD"), anyList())).thenReturn(valid);
        when(coupons.preview(eq(7L), eq("BAD"), anyList())).thenThrow(new IllegalArgumentException("Coupon đã hết hạn"));
        when(loyalty.getBalance(7L)).thenReturn(new LoyaltyBalanceResponse(1L,7L,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null));

        var populate = CheckoutController.class.getDeclaredMethod("populate", Long.class, CheckoutRequest.class,
                CheckoutPreview.class, jakarta.servlet.http.HttpSession.class, org.springframework.ui.Model.class);
        populate.setAccessible(true);
        var model = new ExtendedModelMap();
        populate.invoke(controller, 7L, form, preview, new MockHttpSession(), model);

        assertEquals(List.of(valid), model.getAttribute("couponApplications"));
        assertEquals(Map.of("BAD", "Coupon đã hết hạn"), model.getAttribute("couponErrors"));
        assertEquals(new BigDecimal("1000"), model.getAttribute("couponDiscount"));
        verify(loyalty, never()).previewRedemption(anyLong(), any(), any());
    }
}
