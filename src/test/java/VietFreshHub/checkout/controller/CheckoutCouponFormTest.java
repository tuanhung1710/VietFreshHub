package VietFreshHub.checkout.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.service.CheckoutAddressService;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.coupon.dto.CouponApplicationResponse;
import VietFreshHub.coupon.services.CouponApplicationService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.loyalty.services.LoyaltyService;
import VietFreshHub.loyalty.dto.LoyaltyBalanceResponse;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutCouponFormTest {
    private final CheckoutService checkout = mock(CheckoutService.class);
    private final CouponApplicationService coupons = mock(CouponApplicationService.class);
    private final LoyaltyService loyalty = mock(LoyaltyService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final MockHttpSession session = new MockHttpSession();
    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        var currentCustomer = mock(CurrentCustomerService.class);
        var carts = mock(CartService.class);
        var drafts = new CheckoutDraftStore();
        var cart = CartDto.builder().cartId(10L).totalItems(1)
                .totalAmount(new BigDecimal("100000"))
                .items(List.of(CartItemDto.builder().cartItemId(20L).shopId(30L).build()))
                .build();
        var preview = new CheckoutPreview(cart, List.of(), new BigDecimal("20000"),
                new BigDecimal("20000"), new BigDecimal("120000"), true, "fingerprint");
        token = drafts.issue(session, 1L, preview).token();
        when(currentCustomer.requireCustomerId(authentication)).thenReturn(1L);
        when(carts.getActiveCart(1L)).thenReturn(cart);
        when(checkout.preview(1L, List.of(20L))).thenReturn(preview);
        when(loyalty.getBalance(1L)).thenReturn(new LoyaltyBalanceResponse(null, 1L, 1000, 1000, 0, null));
        when(loyalty.previewRedemption(eq(1L), eq(0), any()))
                .thenReturn(new LoyaltyRedemptionResponse(0, BigDecimal.ZERO, 1000));
        var controller = new CheckoutController(currentCustomer, checkout, drafts,
                mock(CheckoutAddressService.class), carts, coupons, loyalty);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((name, locale) -> name.startsWith("redirect:")
                        ? new RedirectView(name.substring("redirect:".length()))
                        : mock(View.class)).build();
    }

    private void validCoupon() {
        when(coupons.preview(1L, "SAVE10", List.of(20L))).thenReturn(
                new CouponApplicationResponse(2L, "SAVE10", 3L, 30L,
                        new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("90000")));
    }

    @Test
    void pointsPreviewUsesAppliedCouponAndDoesNotRedeem() throws Exception {
        validCoupon();
        when(loyalty.previewRedemption(1L, 100, new BigDecimal("90000")))
                .thenReturn(new LoyaltyRedemptionResponse(100, new BigDecimal("5000"), 900));
        var result = mvc.perform(post("/customer/checkout/points/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponCode", "SAVE10").param("couponInput", "NOT_APPLIED")
                        .param("pointsToUse", "0").param("pointsInput", "100"))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attribute("loyaltyDiscount", new BigDecimal("5000")))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("105000")))
                .andReturn();
        assertEquals(100, ((CheckoutRequest) result.getModelAndView().getModel().get("form")).getPointsToUse());
        verify(loyalty, never()).redeemToOrder(anyLong(), anyLong(), anyInt());
        verify(checkout, never()).confirm(anyLong(), any(), any());
    }

    @Test
    void changingCouponRecalculatesAppliedPointsInsteadOfEditedInput() throws Exception {
        validCoupon();
        when(loyalty.previewRedemption(1L, 100, new BigDecimal("90000")))
                .thenThrow(new IllegalArgumentException("Vượt giới hạn đổi điểm"));
        mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponInput", "SAVE10").param("pointsToUse", "100").param("pointsInput", "200"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyError", "Vượt giới hạn đổi điểm"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("110000")));
    }

    @Test
    void zeroPointsRemovesAppliedPoints() throws Exception {
        mvc.perform(post("/customer/checkout/points/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("pointsToUse", "100").param("pointsInput", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")));
        verify(loyalty).previewRedemption(1L, 0, new BigDecimal("100000"));
    }

    @Test
    void negativePointsRendersErrorWithoutDiscount() throws Exception {
        when(loyalty.previewRedemption(1L, -1, new BigDecimal("100000")))
                .thenThrow(new IllegalArgumentException("Số điểm sử dụng phải từ 0 trở lên"));
        mvc.perform(post("/customer/checkout/points/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("pointsInput", "-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyError", "Số điểm sử dụng phải từ 0 trở lên"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")));
        verify(loyalty, never()).redeemToOrder(anyLong(), anyLong(), anyInt());
    }

    @Test
    void expiredPointsPreviewDoesNotAccessLoyalty() throws Exception {
        mvc.perform(post("/customer/checkout/points/preview")
                        .principal(authentication).session(session).param("checkoutToken", "expired")
                        .param("pointsInput", "100"))
                .andExpect(redirectedUrl("/cart"));
        verifyNoInteractions(loyalty, checkout, coupons);
    }

    @Test
    void previewWithoutAddressTrimsCodeAndDoesNotCreateOrder() throws Exception {
        validCoupon();
        var result = mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session)
                        .param("checkoutToken", token).param("couponInput", "  SAVE10  "))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("couponDiscount", new BigDecimal("10000")))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("110000")))
                .andReturn();
        var form = (CheckoutRequest) result.getModelAndView().getModel().get("form");
        assertNull(form.getAddressId());
        assertEquals("SAVE10", form.getCouponCode());
        assertEquals(token, form.getCheckoutToken());
        verify(checkout, never()).confirm(anyLong(), any(), any());
        verify(coupons, never()).applyToOrder(anyLong(), anyLong(), anyString());
    }

    @Test
    void previewPreservesSelectedAddressAndPaymentMethod() throws Exception {
        validCoupon();
        var result = mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("addressId", "42").param("paymentMethodCode", "COD")
                        .param("couponInput", "SAVE10"))
                .andExpect(status().isOk()).andReturn();
        var form = (CheckoutRequest) result.getModelAndView().getModel().get("form");
        assertEquals(42L, form.getAddressId());
        assertEquals("COD", form.getPaymentMethodCode());
    }

    @Test
    void emptyInputRemovesPreviouslyAppliedCode() throws Exception {
        var result = mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponCode", "SAVE10").param("couponInput", "  "))
                .andExpect(model().attribute("couponDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")))
                .andReturn();
        var form = (CheckoutRequest) result.getModelAndView().getModel().get("form");
        assertEquals("", form.getCouponCode());
        verifyNoInteractions(coupons);
    }

    @Test
    void invalidCouponRendersErrorWithoutDiscount() throws Exception {
        when(coupons.preview(1L, "INVALID", List.of(20L)))
                .thenThrow(new IllegalArgumentException("Mã coupon không tồn tại"));
        mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponInput", "INVALID"))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attribute("couponError", "Mã coupon không tồn tại"))
                .andExpect(model().attribute("couponDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")));
        verify(loyalty, never()).previewRedemption(anyLong(), any(), any());
    }

    @Test
    void expiredTokenRedirectsToCartWithoutPreviewingCoupon() throws Exception {
        mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", "expired")
                        .param("couponInput", "SAVE10"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Phiên đặt hàng đã hết hạn. Vui lòng chọn lại sản phẩm."));
        verifyNoInteractions(checkout, coupons);
    }

    @Test
    void failedConfirmationRecalculatesUsingAppliedCodeInsteadOfEditedInput() throws Exception {
        validCoupon();
        when(checkout.confirm(eq(1L), any(), any())).thenThrow(new CheckoutException("Hết hàng"));
        mvc.perform(post("/customer/checkout")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("addressId", "42").param("paymentMethodCode", "COD")
                        .param("couponCode", "SAVE10").param("couponInput", "NOT_APPLIED"))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().hasErrors())
                .andExpect(model().attribute("couponDiscount", new BigDecimal("10000")))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("110000")));
        verify(checkout).confirm(eq(1L), any(), argThat(form -> "SAVE10".equals(form.getCouponCode())));
        verify(coupons).preview(1L, "SAVE10", List.of(20L));
    }
}
