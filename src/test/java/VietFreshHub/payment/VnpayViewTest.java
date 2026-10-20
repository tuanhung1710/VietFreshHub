package VietFreshHub.payment;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.payment.controller.CustomerVnpayController;
import VietFreshHub.payment.controller.VnpayCallbackController;
import VietFreshHub.payment.dto.VnpayPaymentPage;
import VietFreshHub.payment.service.VnpayPaymentService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class VnpayViewTest {
    final VnpayPaymentService service=mock(VnpayPaymentService.class);
    final CurrentCustomerService customer=mock(CurrentCustomerService.class);
    final Authentication auth=mock(Authentication.class);
    AnnotationConfigWebApplicationContext context; MockMvc mvc;
    @BeforeEach void setup() {
        when(customer.requireCustomerId(auth)).thenReturn(7L);
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(CustomerPaymentHistoryControllerTest.RenderConfiguration.class,
                CustomerVnpayController.class,VnpayCallbackController.class,CheckoutView.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("payments",service); beans.registerSingleton("customer",customer);
            beans.registerSingleton("orders",mock(VietFreshHub.order.service.CustomerOrderQueryService.class));
        });
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.AnonymousAuthenticationToken("test","anonymous",
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        context.refresh(); mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void close() { context.close(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    String page(String state,boolean canPay) throws Exception {
        when(service.page(7L,1L)).thenReturn(new VnpayPaymentPage(1L,"<script>bad</script>",new BigDecimal("12345.50"),
                state,"Thông tin từ máy chủ",LocalDateTime.of(2026,10,20,15,0),canPay));
        return mvc.perform(get("/customer/payments/orders/1").principal(auth)).andReturn().getResponse().getContentAsString();
    }
    @Test void pendingPageRendersAmountEscapedCodeAndPostAction() throws Exception {
        String html=page("PENDING",true);
        assertTrue(html.contains("12.345,50")); assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("/customer/payments/orders/1/pay")); assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("/customer/payments/orders/1/status"));
    }
    @Test void reviewPageDoesNotOfferAnotherPayment() throws Exception {
        String html=page("REVIEW",false); assertFalse(html.contains("/1/pay\""));
    }
    @Test void publicReturnNeverEchoesRawCallbackOrDeclaresPaid() throws Exception {
        String html=mvc.perform(get("/payments/vnpay/return").param("vnp_OrderInfo","<script>bad</script>"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("<script>bad</script>")); assertTrue(html.contains("Không thể xác minh"));
    }
    @Test void checkoutOffersEnabledVnpayWhenServerAllowsIt() throws Exception {
        String html=mvc.perform(get("/checkout-view")).andReturn().getResponse().getContentAsString();
        var input=java.util.regex.Pattern.compile("<input[^>]*value=\"VNPAY\"[^>]*>").matcher(html);
        assertTrue(input.find()); assertFalse(input.group().contains("disabled")); assertTrue(html.contains("value=\"COD\""));
    }
    @Controller static class CheckoutView {
        @GetMapping("/checkout-view") String show(Model model) {
            var cart=CartDto.builder().items(List.of()).totalAmount(BigDecimal.ZERO).build();
            model.addAttribute("form",new CheckoutRequest()); model.addAttribute("cart",cart);
            model.addAttribute("checkout",new CheckoutPreview(cart,List.of(),null,Map.of(),null,null,true,"test",null,false));
            model.addAttribute("vnpayAvailable",true); model.addAttribute("shopGroups",Map.of());
            return "customer/checkout";
        }
    }
}
