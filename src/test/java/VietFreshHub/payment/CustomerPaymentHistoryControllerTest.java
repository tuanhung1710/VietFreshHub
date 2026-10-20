package VietFreshHub.payment;

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
class CustomerPaymentHistoryControllerTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CustomerPaymentHistoryService payments = mock(CustomerPaymentHistoryService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, CustomerPaymentHistoryController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("payments", payments);
            beans.registerSingleton("orders", mock(CustomerOrderQueryService.class));
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
    @Test void rendersEmptyHistoryWithoutInventingPayments() throws Exception {
        when(payments.history(eq(7L), any())).thenReturn(Page.empty());
        String html = mvc.perform(get("/customer/payments").principal(auth)).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Bạn chưa có lịch sử thanh toán")); assertFalse(html.contains("Bản ghi #"));
    }
    @Test void rendersCodStateEscapedDataAndPagingFilters() throws Exception {
        when(payments.history(eq(7L), any())).thenReturn(new PageImpl<>(List.of(row()), PageRequest.of(0,10), 11));
        String html = mvc.perform(get("/customer/payments").principal(auth).param("keyword", "VF-10")
                .param("status", "PENDING").param("fromDate", "2026-10-20")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Chờ thu tiền")); assertTrue(html.contains("10:00 20/10/2026"));
        assertTrue(html.contains("&lt;script&gt;")); assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("/customer/orders/10")); assertTrue(html.contains("page=2"));
        assertTrue(html.contains("fromDate=2026-10-20")); assertTrue(html.contains("keyword=VF-10"));
        assertTrue(html.contains("12.345,50"));
        assertFalse(html.contains("method=\"post\""));
    }
    @Test void invalidBindingDoesNotQueryHistory() throws Exception {
        var response = mvc.perform(get("/customer/payments").principal(auth).param("page", "abc"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertTrue(response.getContentAsString().contains("Bộ lọc không hợp lệ"));
        verifyNoInteractions(payments);
    }
}
