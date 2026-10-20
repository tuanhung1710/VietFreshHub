package VietFreshHub.checkout;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.checkout.controller.CheckoutController;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.service.*;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.vietmap.service.VietMapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A commit exception does not establish that the DB did not commit the order. */
class CheckoutFailureRecoveryTest {
    final CurrentCustomerService current=mock(CurrentCustomerService.class);
    final CheckoutService checkout=mock(CheckoutService.class);
    final CheckoutShippingQuoteService shipping=mock(CheckoutShippingQuoteService.class);
    final CheckoutDraftStore drafts=new CheckoutDraftStore();
    final CartService carts=mock(CartService.class);
    final Authentication authentication=mock(Authentication.class);
    final CheckoutController controller=new CheckoutController(current,checkout,shipping,drafts,
            mock(CheckoutAddressService.class),mock(CustomerAddressService.class),mock(VietMapService.class),carts,mock(VietFreshHub.payment.service.VnpayPaymentService.class));

    static Stream<RuntimeException> infrastructureFailures() {
        return Stream.of(new CannotAcquireLockException("test lock failure"),
                new DataIntegrityViolationException("test constraint failure"),
                new DataAccessResourceFailureException("test connection lost"),
                new TransactionSystemException("test commit outcome unknown"));
    }
    private CheckoutDraft draft(MockHttpSession session) {
        var cart=CartDto.builder().cartId(11L).userId(7L).items(List.of(CartItemDto.builder().cartItemId(1L).build())).build();
        return drafts.issue(session,7L,new CheckoutPreview(cart,List.of(),5L,Map.of(),BigDecimal.ZERO,BigDecimal.ONE,true,"f",null,false));
    }
    @ParameterizedTest @MethodSource("infrastructureFailures")
    void infrastructureFailureKeepsOriginalDraftAndDoesNotRequeryOrRequote(RuntimeException failure) {
        var session=new MockHttpSession(); var draft=draft(session);
        var form=new CheckoutRequest(); form.setAddressId(5L); form.setCheckoutToken(draft.token());
        when(current.requireCustomerId(authentication)).thenReturn(7L);
        when(checkout.confirm(7L,draft,form)).thenThrow(failure);
        var model=new ExtendedModelMap(); var response=new MockHttpServletResponse();
        String view=controller.confirm(form,new BeanPropertyBindingResult(form,"form"),authentication,session,model,response);
        assertEquals("customer/checkout-unavailable",view); assertEquals(503,response.getStatus());
        assertEquals("VF-"+draft.token(),model.get("orderCode")); assertEquals(draft.token(),model.get("checkoutToken"));
        assertSame(draft,drafts.find(session,draft.token(),7L)); assertEquals(draft.token(),form.getCheckoutToken());
        verify(checkout).confirm(7L,draft,form); verifyNoMoreInteractions(checkout);
        verifyNoInteractions(shipping,carts); // Do not issue another DB/API call in the failure handler.
    }
    @Test void successfulReplayStillRedirectsToConfirmation() {
        var session=new MockHttpSession(); var draft=draft(session);
        var form=new CheckoutRequest(); form.setAddressId(5L); form.setCheckoutToken(draft.token());
        when(current.requireCustomerId(authentication)).thenReturn(7L);
        when(checkout.confirm(7L,draft,form)).thenReturn(new CheckoutResult(100L,"VF-"+draft.token(),BigDecimal.ONE));
        assertEquals("redirect:/customer/orders/100/confirmation",controller.confirm(form,new BeanPropertyBindingResult(form,"form"),
                authentication,session,new ExtendedModelMap(),new MockHttpServletResponse()));
        verifyNoInteractions(shipping,carts);
    }
    @Test void actualPostRendersRecoveryLinksWithoutAutoResubmittingOrClaimingOrderFailed() throws Exception {
        var session=new MockHttpSession(); var draft=draft(session);
        when(current.requireCustomerId(authentication)).thenReturn(7L);
        when(checkout.confirm(eq(7L),eq(draft),any())).thenThrow(new TransactionSystemException("test commit failure"));
        try(var context=new StaticWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.refresh();
            var resolver=new SpringResourceTemplateResolver(); resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine=new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
            var views=new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            // No security filter chain here: ownership/CSRF need separate security integration tests.
            var mvc=MockMvcBuilders.standaloneSetup(controller).setViewResolvers(views).build();
            var response=mvc.perform(post("/customer/checkout").session(session).principal(authentication)
                    .param("addressId","5").param("paymentMethodCode","COD").param("checkoutToken",draft.token()))
                    .andReturn().getResponse();
            assertEquals(503,response.getStatus());
            String html=response.getContentAsString();
            assertTrue(html.contains("Chưa xác nhận được kết quả đặt hàng"));
            assertTrue(html.contains("/customer/orders?keyword=VF-"+draft.token()));
            assertTrue(html.contains("/customer/checkout?checkoutToken="+draft.token()));
            assertFalse(html.contains("<form")); assertFalse(html.contains("Đơn hàng chưa được tạo"));
            Path output=Path.of("target","tv3-task03"); Files.createDirectories(output);
            Files.writeString(output.resolve("checkout-unavailable.html"),html,StandardCharsets.UTF_8);
        }
        verifyNoInteractions(shipping,carts);
    }
}
