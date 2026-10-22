package VietFreshHub.order;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.controller.CustomerOrderController;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.cart.service.CartService;
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
class CustomerOrdersViewTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CustomerOrderService orders = mock(CustomerOrderService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, CustomerOrderController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("orders", orders);
            beans.registerSingleton("carts", mock(CartService.class));
            beans.registerSingleton("readOrders", mock(CustomerOrderQueryService.class));
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
        @Bean org.springframework.web.servlet.support.RequestDataValueProcessor requestDataValueProcessor() {
            return new org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor();
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
    OrderHistorySummary card(String delivery, boolean cancellable) {
        return new OrderHistorySummary(List.of(
                new OrderHistorySummary.Product("Táo <script>alert(1)</script>","Hộp 1 kg",2,"/fruit.png"),
                new OrderHistorySummary.Product("Xoài cát","Loại 1",1,null)),2,3,
                List.of(new OrderHistorySummary.Shop("Vườn Xanh","Hoàn thành",null,null),
                        new OrderHistorySummary.Shop("Fresh Market","Vận chuyển",delivery,
                                delivery == null ? null : switch (delivery) {
                                    case "FAILED" -> "Giao hàng thất bại";
                                    case "REDELIVERY_SCHEDULED" -> "Chờ giao lại";
                                    case "DELIVERED" -> "Đã giao hàng";
                                    default -> "Đang giao";
                                })),
                List.of(new OrderDetailsResponse.PaymentInfo(5L,"Thanh toán khi nhận hàng","COD","PENDING","Chờ thu COD",BigDecimal.TEN,null)),
                1,cancellable,true,true);
    }
    void page(String stage, OrderHistorySummary summary) {
        var row = new OrderListRow(10L,"VF-10",Order.Status.PENDING,"UNPAID",new BigDecimal("381000"),LocalDateTime.of(2026,10,20,3,0),stage);
        when(orders.history(eq(7L),any())).thenReturn(new PageImpl<>(List.of(row),PageRequest.of(0,10),11));
        when(orders.historySummaries(7L,List.of(10L))).thenReturn(java.util.Map.of(10L,summary));
    }
    String render() throws Exception {
        return mvc.perform(get("/customer/orders").principal(auth).requestAttr(org.springframework.security.web.csrf.CsrfToken.class.getName(),
                new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN","_csrf","test-token")))
                .andReturn().getResponse().getContentAsString();
    }
    @Test void cardsRenderSnapshotsPaymentShopDeliveryAndRealPostActions() throws Exception {
        page("WAITING_CONFIRMATION",card("FAILED",true));
        String html = render();
        for (String text : List.of("Chờ xác nhận","Chờ thu COD","381.000","2 phân loại","3 sản phẩm",
                "Vườn Xanh","Fresh Market","Giao hàng thất bại","Hộp 1 kg","Chưa có ảnh","Xem chi tiết","Theo dõi","Mua lại","Hủy đơn")) assertTrue(html.contains(text),text);
        assertTrue(html.contains("&lt;script&gt;")); assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("/customer/orders/10/cancel")); assertTrue(html.contains("/customer/orders/10/reorder"));
        assertTrue(html.contains("name=\"_csrf\"")); assertTrue(html.contains("test-token"));
        assertTrue(html.contains("10:00 · 20/10/2026"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(".document-analysis"));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase3-orders-preview.html"),html);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(CustomerOrderStage.class)
    void primaryBadgeUsesPhaseTwoStage(CustomerOrderStage stage) throws Exception {
        page(stage.name(),card(null,false));
        String html = render();
        assertTrue(html.contains("data-stage=\""+stage.name()+"\""));
        assertTrue(html.contains(stage.label()));
        assertFalse(html.contains("/customer/orders/10/cancel"));
        assertFalse(html.contains("RETURN_REFUND"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"OUT_FOR_DELIVERY","FAILED","REDELIVERY_SCHEDULED","DELIVERED"})
    void deliveryLabelsStayAttachedToTheirShop(String delivery) throws Exception {
        var summary = card(delivery,false);
        page("SHIPPING",summary);
        String html = render();
        assertTrue(html.contains("data-delivery=\""+delivery+"\""));
        assertTrue(html.contains(summary.shops().get(1).deliveryLabel()));
        assertTrue(html.contains("data-stage=\"SHIPPING\""));
    }
    @Test void filtersSurvivePaginationAndEmptySearchCanReset() throws Exception {
        page("PREPARING",card(null,false));
        String html = mvc.perform(get("/customer/orders").principal(auth).param("keyword","VF-10")
                .param("status","PREPARING").param("fromDate","2026-10-20").param("toDate","2026-10-21"))
                .andReturn().getResponse().getContentAsString();
        for (String value : List.of("page=2","keyword=VF-10","status=PREPARING","fromDate=2026-10-20","toDate=2026-10-21")) assertTrue(html.contains(value),value);
        when(orders.history(eq(7L),any())).thenReturn(Page.empty());
        html = mvc.perform(get("/customer/orders").principal(auth).param("keyword","missing")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Chưa có đơn hàng phù hợp")); assertTrue(html.contains("Xóa bộ lọc"));
        assertFalse(html.contains("/cancel"));
        html = render(); assertTrue(html.contains("Bạn chưa có đơn hàng nào"));
    }
}
