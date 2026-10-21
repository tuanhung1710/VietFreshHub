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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutCouponFormTest {
    private final VietFreshHub.checkout.service.CheckoutShippingQuoteService shipping = mock(VietFreshHub.checkout.service.CheckoutShippingQuoteService.class);
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
        var preview = new CheckoutPreview(cart, List.of(), null, java.util.Map.of(),
                new BigDecimal("20000"), new BigDecimal("120000"), true, "fingerprint", null, false);
        token = drafts.issue(session, 1L, preview).token();
        when(currentCustomer.requireCustomerId(authentication)).thenReturn(1L);
        when(carts.getActiveCart(1L)).thenReturn(cart);
        when(checkout.preview(1L, List.of(20L))).thenReturn(preview);
        when(loyalty.getBalance(1L)).thenReturn(new LoyaltyBalanceResponse(null, 1L, new BigDecimal("20000"), new BigDecimal("20000"), BigDecimal.ZERO, null));
        when(loyalty.previewRedemption(eq(1L), eq(BigDecimal.ZERO), any()))
                .thenReturn(new LoyaltyRedemptionResponse(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20000")));
        when(shipping.quote(eq(1L), any(), nullable(Long.class))).thenAnswer(call -> {
            CheckoutPreview input = call.getArgument(1);
            Long addressId = call.getArgument(2);
            return new CheckoutPreview(input.cart(), input.addresses(), addressId, input.shopQuotes(),
                    input.shippingFee(), input.grandTotal(), input.codAvailable(), input.fingerprint(),
                    input.shippingError(), input.deliveryOutOfRange());
        });
        var controller = new CheckoutController(currentCustomer, checkout, shipping, drafts,
                mock(CheckoutAddressService.class),
                mock(VietFreshHub.customer.service.CustomerAddressService.class),
                mock(VietFreshHub.vietmap.service.VietMapService.class), carts, coupons, loyalty);
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

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1000.5", "10000000000000000"})
    void invalidHiddenRewardAmountCannotConfirmOrder(String input) throws Exception {
        when(loyalty.previewRedemption(eq(1L), eq(new BigDecimal(input)), any()))
                .thenThrow(new IllegalArgumentException("Tiền thưởng phải là số đồng nguyên hợp lệ"));
        mvc.perform(post("/customer/checkout").principal(authentication).session(session)
                        .param("checkoutToken", token).param("addressId", "42")
                        .param("rewardAmountToUse", input))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "rewardAmountToUse"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO));
        verify(checkout, never()).confirm(anyLong(), any(), any());
        verify(loyalty, never()).redeemToOrder(anyLong(), anyLong(), any());
    }

    @Test
    void fractionalRewardPreviewRendersErrorWithoutCreatingOrder() throws Exception {
        when(loyalty.previewRedemption(1L, new BigDecimal("1000.5"), new BigDecimal("100000")))
                .thenThrow(new IllegalArgumentException("Tiền thưởng phải là số đồng nguyên"));
        mvc.perform(post("/customer/checkout/reward/preview").principal(authentication).session(session)
                        .param("checkoutToken", token).param("rewardAmountInput", "1000.5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyError", "Tiền thưởng phải là số đồng nguyên"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO));
        verify(checkout, never()).confirm(anyLong(), any(), any());
    }

    @Test
    void rewardPreviewUsesAppliedCouponAndDoesNotRedeem() throws Exception {
        validCoupon();
        when(loyalty.previewRedemption(1L, new BigDecimal("5000"), new BigDecimal("90000")))
                .thenReturn(new LoyaltyRedemptionResponse(new BigDecimal("5000"), new BigDecimal("5000"), new BigDecimal("15000")));
        var result = mvc.perform(post("/customer/checkout/reward/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponCode", "SAVE10").param("couponInput", "NOT_APPLIED")
                        .param("rewardAmountToUse", "0").param("rewardAmountInput", "5000"))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attribute("loyaltyDiscount", new BigDecimal("5000")))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("105000")))
                .andReturn();
        assertEquals(new BigDecimal("5000"), ((CheckoutRequest) result.getModelAndView().getModel().get("form")).getRewardAmountToUse());
        verify(loyalty, never()).redeemToOrder(anyLong(), anyLong(), any(BigDecimal.class));
        verify(checkout, never()).confirm(anyLong(), any(), any());
    }

    @Test
    void changingCouponRecalculatesAppliedRewardInsteadOfEditedInput() throws Exception {
        validCoupon();
        when(loyalty.previewRedemption(1L, new BigDecimal("5000"), new BigDecimal("90000")))
                .thenThrow(new IllegalArgumentException("Vượt giới hạn dùng tiền thưởng"));
        mvc.perform(post("/customer/checkout/coupon/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("couponInput", "SAVE10").param("rewardAmountToUse", "5000").param("rewardAmountInput", "200"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyError", "Vượt giới hạn dùng tiền thưởng"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("110000")));
    }

    @Test
    void zeroRewardRemovesAppliedReward() throws Exception {
        mvc.perform(post("/customer/checkout/reward/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("rewardAmountToUse", "5000").param("rewardAmountInput", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")));
        verify(loyalty).previewRedemption(1L, BigDecimal.ZERO, new BigDecimal("100000"));
    }

    @Test
    void negativeRewardRendersErrorWithoutDiscount() throws Exception {
        when(loyalty.previewRedemption(1L, new BigDecimal("-1"), new BigDecimal("100000")))
                .thenThrow(new IllegalArgumentException("Số tiền thưởng sử dụng phải từ 0 trở lên"));
        mvc.perform(post("/customer/checkout/reward/preview")
                        .principal(authentication).session(session).param("checkoutToken", token)
                        .param("rewardAmountInput", "-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loyaltyError", "Số tiền thưởng sử dụng phải từ 0 trở lên"))
                .andExpect(model().attribute("loyaltyDiscount", BigDecimal.ZERO))
                .andExpect(model().attribute("checkoutTotal", new BigDecimal("120000")));
        verify(loyalty, never()).redeemToOrder(anyLong(), anyLong(), any(BigDecimal.class));
    }

    @Test
    void expiredRewardPreviewDoesNotAccessLoyalty() throws Exception {
        mvc.perform(post("/customer/checkout/reward/preview")
                        .principal(authentication).session(session).param("checkoutToken", "expired")
                        .param("rewardAmountInput", "5000"))
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
        verify(coupons).getAvailableCoupons(1L, List.of(20L));
        verifyNoMoreInteractions(coupons);
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

    @Test
    void businessCheckoutErrorPreservesAppliedRewardCouponAndAddress() throws Exception {
        validCoupon();
        when(checkout.confirm(eq(1L), any(), any())).thenThrow(new CheckoutException("Coupon vừa hết lượt"));
        when(loyalty.previewRedemption(1L, new BigDecimal("5000"), new BigDecimal("90000")))
                .thenReturn(new LoyaltyRedemptionResponse(new BigDecimal("5000"), new BigDecimal("5000"), new BigDecimal("15000")));
        var result = mvc.perform(post("/customer/checkout").principal(authentication).session(session)
                        .param("checkoutToken", token).param("addressId", "42").param("paymentMethodCode", "COD")
                        .param("couponCode", "SAVE10").param("rewardAmountToUse", "5000"))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attributeHasErrors("form"))
                .andExpect(model().attribute("loyaltyDiscount", new BigDecimal("5000"))).andReturn();
        var form = (CheckoutRequest) result.getModelAndView().getModel().get("form");
        assertEquals("SAVE10", form.getCouponCode()); assertEquals(42L, form.getAddressId());
        assertEquals("COD", form.getPaymentMethodCode()); assertEquals(new BigDecimal("5000"), form.getRewardAmountToUse());
        var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel().get("org.springframework.validation.BindingResult.form");
        assertEquals("Coupon vừa hết lượt", binding.getGlobalError().getDefaultMessage());
    }

    @Test
    void transientCheckoutFailureReturnsRetryMessageAndKeepsForm() throws Exception {
        when(checkout.confirm(eq(1L), any(), any())).thenThrow(new org.springframework.dao.CannotAcquireLockException("internal lock details"));
        var result = mvc.perform(post("/customer/checkout").principal(authentication).session(session)
                        .param("checkoutToken", token).param("addressId", "42").param("paymentMethodCode", "COD"))
                .andExpect(status().isServiceUnavailable()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attributeHasErrors("form")).andReturn();
        var form = (CheckoutRequest) result.getModelAndView().getModel().get("form");
        assertEquals(42L, form.getAddressId()); assertEquals("COD", form.getPaymentMethodCode());
        var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel().get("org.springframework.validation.BindingResult.form");
        assertEquals("Đơn hàng chưa được tạo. Vui lòng kiểm tra lại và thử xác nhận lần nữa.", binding.getGlobalError().getDefaultMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "integrity", "unexpected"})
    void systemCheckoutFailuresPropagateInsteadOfBecomingBusinessErrors(String kind) {
        RuntimeException failure = switch (kind) {
            case "schema" -> new org.springframework.jdbc.BadSqlGrammarException("checkout", "SELECT test", new java.sql.SQLException("invalid object"));
            case "integrity" -> new org.springframework.dao.DataIntegrityViolationException("constraint");
            default -> new IllegalStateException("Loyalty rule is missing");
        };
        when(checkout.confirm(eq(1L), any(), any())).thenThrow(failure);
        var thrown = assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(post("/customer/checkout")
                .principal(authentication).session(session).param("checkoutToken", token).param("addressId", "42")));
        assertSame(failure, thrown.getCause());
        verify(checkout, never()).preview(anyLong(), anyList());
    }
}
