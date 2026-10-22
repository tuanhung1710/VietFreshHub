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
        for (String label : List.of("Tất cả", "Chờ thanh toán / Chờ thu COD", "Đang xử lý", "Đã thanh toán", "Thanh toán thất bại", "Đã hủy giao dịch", "Đã hoàn tiền", "Hoàn tiền một phần")) assertTrue(html.contains(label));
        assertTrue(html.contains("Chờ thu COD")); assertTrue(html.contains("10:00 20/10/2026"));
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
    String render() throws Exception {
        return mvc.perform(get("/customer/payments").principal(auth)).andReturn().getResponse().getContentAsString();
    }
    String card(String html, long id) {
        var pattern = java.util.regex.Pattern.compile("(?s)<article[^>]*data-payment-id=\""+id+"\".*?</article>");
        var matcher = pattern.matcher(html); assertTrue(matcher.find(),"Missing card "+id); return matcher.group();
    }
    PaymentHistoryRow record(long id, String method, Payment.Status status, String reference, LocalDateTime paidAt) {
        return new PaymentHistoryRow(id,10L,"VF-10",method.equals("COD") ? "Thanh toán khi nhận hàng" : "Thanh toán trực tuyến",
                method,new BigDecimal("106000"),status,LocalDateTime.of(2026,10,20,3,0),paidAt,reference);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "COD,PENDING,Chờ thu COD", "VNPAY,PENDING,Chờ thanh toán", "VNPAY,PROCESSING,Đang xử lý",
        "VNPAY,PAID,Đã thanh toán", "VNPAY,FAILED,Thanh toán thất bại", "COD,CANCELLED,Đã hủy giao dịch",
        "VNPAY,REFUNDED,Đã hoàn tiền", "VNPAY,PARTIALLY_REFUNDED,Hoàn tiền một phần"})
    void eachBadgeDescribesOnlyItsPayment(String method, Payment.Status status, String expected) throws Exception {
        when(payments.history(eq(7L),any())).thenReturn(new PageImpl<>(List.of(record(1L,method,status,null,null))));
        String html = render(); String payment = card(html,1L);
        assertTrue(payment.contains("data-payment-status=\""+status.name()+"\""));
        assertTrue(payment.contains(expected));
        assertEquals(method.equals("COD") && status==Payment.Status.PENDING,payment.contains("Hệ thống chưa xác nhận đã thu tiền COD"));
        assertFalse(payment.contains("Thời điểm ghi nhận thanh toán"));
        assertFalse(payment.contains("Mã tham chiếu thanh toán"));
        assertFalse(html.contains("Trạng thái đơn hàng")); assertFalse(html.contains("WAITING_PAYMENT"));
        assertFalse(html.contains("method=\"post\""));
    }
    @Test void distinctAttemptsSameOrderKeepTheirOwnIdStatusAndOptionalFields() throws Exception {
        var rows = List.of(record(1L,"VNPAY",Payment.Status.FAILED,null,null),
                record(2L,"VNPAY",Payment.Status.PAID,"VN-<script>alert(1)</script>",LocalDateTime.of(2026,10,20,4,30)));
        when(payments.history(eq(7L),any())).thenReturn(new PageImpl<>(rows));
        String html = render(); String failed = card(html,1L); String paid = card(html,2L);
        assertTrue(failed.contains("Bản ghi #1")); assertTrue(paid.contains("Bản ghi #2"));
        assertTrue(failed.contains("Thanh toán thất bại")); assertTrue(paid.contains("Đã thanh toán"));
        assertTrue(failed.contains("/customer/orders/10")); assertTrue(paid.contains("/customer/orders/10"));
        assertFalse(failed.contains("Mã tham chiếu thanh toán")); assertFalse(failed.contains("Thời điểm ghi nhận thanh toán"));
        assertTrue(paid.contains("VN-&lt;script&gt;")); assertFalse(paid.contains("<script>alert(1)</script>"));
        assertTrue(paid.contains("11:30 20/10/2026")); assertTrue(paid.contains("106.000"));
    }
    @Test void filteredEmptyStateAndSelectedStatusKeepAllPaginationParameters() throws Exception {
        when(payments.history(eq(7L),any())).thenReturn(new PageImpl<>(List.of(row()),PageRequest.of(0,10),11));
        String html = mvc.perform(get("/customer/payments").principal(auth).param("keyword","VF-10").param("status","PENDING")
                .param("fromDate","2026-10-20").param("toDate","2026-10-21")).andReturn().getResponse().getContentAsString();
        for (String value : List.of("page=2","keyword=VF-10","status=PENDING","fromDate=2026-10-20","toDate=2026-10-21")) assertTrue(html.contains(value),value);
        assertTrue(html.matches("(?s).*<option[^>]*value=\"PENDING\"[^>]*selected=\"selected\".*"));
        when(payments.history(eq(7L),any())).thenReturn(Page.empty());
        html = mvc.perform(get("/customer/payments").principal(auth).param("keyword","missing")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Không tìm thấy thanh toán phù hợp")); assertTrue(html.contains("Xóa bộ lọc"));
        assertFalse(html.contains("data-payment-id="));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"status,SHIPPING","page,0","fromDate,not-a-date"})
    void invalidFiltersDoNotCallThePaymentService(String key, String value) throws Exception {
        String html = mvc.perform(get("/customer/payments").principal(auth).param(key,value)).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Bộ lọc không hợp lệ")); verifyNoInteractions(payments);
    }
    @Test void unauthenticatedIdentityCannotReachThePaymentService() {
        when(current.requireCustomerId(auth)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
        assertThrows(Exception.class,() -> mvc.perform(get("/customer/payments").principal(auth)));
        verifyNoInteractions(payments);
    }
    @Test void exportActualTemplateForResponsiveReview() throws Exception {
        var rows = List.of(record(101L,"COD",Payment.Status.PENDING,null,null),
                record(102L,"VNPAY",Payment.Status.PAID,"VFH-20261020-102",LocalDateTime.of(2026,10,20,4,30)),
                record(103L,"VNPAY",Payment.Status.FAILED,null,null));
        when(payments.history(eq(7L),any())).thenReturn(new PageImpl<>(rows,PageRequest.of(0,10),11));
        String html = render(); assertTrue(html.contains("payment-history-page"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(".document-analysis"));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase4-payments-preview.html"),html);
    }
}
