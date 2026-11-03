package VietFreshHub.order;

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
class PurchaseHistoryUiRenderTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final VietFreshHub.order.service.ShopCustomerOrderService purchases = mock(VietFreshHub.order.service.ShopCustomerOrderService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, VietFreshHub.order.controller.ShopCustomerOrderController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("purchases", purchases);
            beans.registerSingleton("vnpay", mock(VietFreshHub.payment.vnpay.VnpayPaymentService.class));
            beans.registerSingleton("orders", mock(VietFreshHub.order.service.ShopCustomerOrderQueryService.class));
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
    PaymentHistoryRow row() {
        return new PaymentHistoryRow(1L, 10L, "VF-10", "<script>alert(1)</script>", "COD", new BigDecimal("12345.50"),
                Payment.Status.PENDING, LocalDateTime.of(2026,10,20,3,0), null);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PENDING", "OUT_FOR_DELIVERY", "CANCELLED"})
    void rendersShopCardsAndPreservesPagination(String status) throws Exception {
        var product = new VietFreshHub.order.dto.OrderHistorySummary.Product("Táo Gala", "1 kg", 2, "/images/apple.jpg");
        var row = new VietFreshHub.order.dto.ShopOrderListRow(115L, 10L, "Vườn Nhà Sơn", status, "UNPAID", new BigDecimal("74000"), LocalDateTime.of(2026,11,3,1,30), "Chưa thanh toán", List.of(product), 4);
        when(purchases.history(eq(7L), any())).thenReturn(new PageImpl<>(List.of(row),PageRequest.of(0,20),41));
        var response = mvc.perform(get("/customer/orders").principal(auth).param("size","20").param("status",status)).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        String html=response.getContentAsString();
        assertTrue(html.contains("purchase-sidebar"));
        assertTrue(html.contains("VFH-000115") || html.contains("VFH-00115"));
        assertTrue(html.contains("/images/apple.jpg"));
        assertTrue(html.contains("4 sản phẩm từ 1 cửa hàng"));
        assertTrue(html.contains("size=20"));
        assertTrue(html.contains("page=2"));
        assertTrue(html.contains("historyPageSize"));
        assertFalse(html.contains("reorder"));
        assertEquals(status.equals("CANCELLED"),html.contains("Đơn hàng đã hủy."));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/purchase-history-"+status+".html"),html);
    }
}
