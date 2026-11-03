package VietFreshHub.order;

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
class ShopOrderDetailUiRenderTest {
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
        final CustomerOrderQueryService orders; PreviewController(CustomerOrderQueryService orders){this.orders=orders;}
        @org.springframework.web.bind.annotation.GetMapping("/preview") String preview(org.springframework.ui.Model model){
            var order=orders.getDetails(7L,1L); model.addAttribute("order",order);model.addAttribute("receipt",order);model.addAttribute("shopOrderId",10L);model.addAttribute("canRetryVnpayPayment",true);model.addAttribute("actions",new OrderActionsResponse(true,"",""));return "customer/shop-order-detail";
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PENDING","CANCELLED"})
    void rendersExistingReceiptDataAndActions(String shopStatus) throws Exception {
        var amount=new BigDecimal("178000");
        var placed=LocalDateTime.of(2026,11,2,14,28);
        var item=new OrderDetailsResponse.Item("Táo Gala","Khay 4 quả","APPLE-1","/images/apple.jpg",2,new BigDecimal("89000"),amount);
        var shop=new OrderDetailsResponse.ShopGroup(10L,"Cửa hàng trái cây","Chờ xác nhận",BigDecimal.ZERO,amount,List.of(item),amount,BigDecimal.ZERO,shopStatus,null);
        var payment=new OrderDetailsResponse.PaymentInfo(1L,"Thanh toán khi nhận hàng","COD","PENDING","Chưa thanh toán",amount,null);
        var order=new OrderDetailsResponse(1L,"VFH-001","Chờ xác nhận","Chưa thanh toán","Nguyễn An","0901234567","Địa chỉ nhận hàng",amount,BigDecimal.ZERO,BigDecimal.ZERO,amount,placed,List.of(shop),List.of(payment));
        when(orders.getDetails(7L,1L)).thenReturn(order);
        when(lifecycle.tracking(7L,1L)).thenReturn(new OrderTrackingResponse(1L,"VFH-001","Chờ xác nhận","PENDING",List.of(new OrderTrackingResponse.ShopTracking(10L,"Cửa hàng trái cây","Chờ xác nhận","PENDING",null,null,List.of(new OrderTrackingResponse.Event(placed,"Đã đặt hàng","Đã nhận đơn","CREATED"))))));
        var response=mvc.perform(get("/preview").principal(auth)).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        String html=response.getContentAsString();
        for(String expected:List.of("178.000","Nguyễn An","Chờ xác nhận","Chưa thanh toán","receipt-info-grid","/customer/orders/10/tracking")) assertTrue(html.contains(expected),expected);
        assertTrue(html.contains("action=\"/customer/orders/shops/10/cancel\""));
        assertTrue(html.contains("/customer/orders/1/retry-payment"));
        assertFalse(html.contains("mockup-steps"));
        assertEquals("CANCELLED".equals(shopStatus),html.contains("/reorder"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/ui-preview"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/ui-preview/shop-order-detail.html"),html);
    }
}
