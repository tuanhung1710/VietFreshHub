package VietFreshHub.order;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.cart.dto.*;
import VietFreshHub.order.controller.CustomerOrderDetailsController;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.dto.*;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.payment.controller.CustomerPaymentHistoryController;
import VietFreshHub.payment.dto.*;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.service.CustomerPaymentHistoryService;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real MVC binding and full template rendering; security chain is covered in CustomerHttpHarness. */
class CheckoutUiRenderTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CustomerOrderQueryService orders = mock(CustomerOrderQueryService.class);
    final CustomerOrderService lifecycle = mock(CustomerOrderService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, PreviewController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("lifecycle", lifecycle);
            beans.registerSingleton("orders", orders);
        });
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.AnonymousAuthenticationToken("test", "anonymous",
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    static class RenderConfiguration {
        @Bean org.springframework.security.access.expression.SecurityExpressionHandler<org.springframework.security.web.FilterInvocation> webSecurityExpressionHandler() {
            return new org.springframework.security.config.annotation.web.builders.WebSecurity(
                    org.springframework.security.config.ObjectPostProcessor.identity()).getExpressionHandler();
        }
        @Bean ThymeleafViewResolver views(ApplicationContext context) {
            var resolver = new SpringResourceTemplateResolver(); resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver); engine.addDialect(new SpringSecurityDialect());
            var views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            return views;
        }
    }
    @AfterEach void close() { context.close(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @org.springframework.stereotype.Controller static class PreviewController {
        @org.springframework.web.bind.annotation.GetMapping("/preview") String preview(org.springframework.ui.Model model){
            var item=CartItemDto.builder().cartItemId(13L).variantId(2L).shopId(1L).shopName("Vườn Nhà Sơn").productName("Táo Gala").variantName("Khay 4 quả").quantity(1).unitPriceSnapshot(new BigDecimal("59000")).itemSubtotal(new BigDecimal("59000")).availableStock(5).purchasable(true).imageUrl("/images/apple.jpg").build();
            var cart=CartDto.builder().cartId(1L).items(List.of(item)).totalAmount(new BigDecimal("59000")).build();
            var form=new CheckoutRequest();form.setAddressId(1L);form.setCheckoutToken("0123456789abcdef0123456789abcdef");
            var preview=new CheckoutPreview(cart,List.of(new CheckoutPreview.AddressOption(1L,"Trần Quốc Bảo","0987253641","18 Trần Hưng Đạo",true,true)),1L,java.util.Map.of(),null,null,true,true,"fingerprint",null,true);
            model.addAttribute("cart",cart);model.addAttribute("checkout",preview);model.addAttribute("form",form);model.addAttribute("shopGroups",java.util.Map.of(1L,cart.getItems()));return "customer/checkout";
        }
    }
    @Test void rendersCheckoutAndKeepsOutOfRangeCheckoutDisabled() throws Exception {
        var response=mvc.perform(get("/preview")).andReturn().getResponse();assertEquals(200,response.getStatus());String html=response.getContentAsString();
        for(String value:List.of("checkout-page.css","checkout-address-panel","checkout-payment-panel","checkout-products-panel","59.000","Không có phương thức vận chuyển phù hợp","name=\"checkoutToken\"","name=\"addressId\"","name=\"paymentMethodCode\"","action=\"/customer/checkout\""))assertTrue(html.contains(value),value);
        var confirm=html.substring(html.lastIndexOf("<button"),html.indexOf("</button>",html.lastIndexOf("<button")));assertTrue(confirm.contains("disabled=\"disabled\""));assertTrue(confirm.contains("data-confirm-order"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/ui-preview"));java.nio.file.Files.writeString(java.nio.file.Path.of("target/ui-preview/checkout.html"),html);
    }
}
