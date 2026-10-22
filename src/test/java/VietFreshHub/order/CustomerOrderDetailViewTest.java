package VietFreshHub.order;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.controller.CustomerOrderDetailsController;
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
class CustomerOrderDetailViewTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CustomerOrderService orders = mock(CustomerOrderService.class);
    final CustomerOrderQueryService reads = mock(CustomerOrderQueryService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, CustomerOrderDetailsController.class,
                VietFreshHub.order.controller.CustomerOrderController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("lifecycle", orders);
            beans.registerSingleton("carts", mock(CartService.class));
            beans.registerSingleton("readOrders", reads);
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
    static BigDecimal money(String value) { return new BigDecimal(value); }
    final LocalDateTime time=LocalDateTime.of(2026,10,20,10,0);
    OrderDetailsResponse.ShopGroup shop(long id,String state,String delivery) {
        var item=new OrderDetailsResponse.Item("Xoài cát", "Hộp 1 kg","SKU-OLD",id==20?"/fruit.png":null,2,
                money("50000"),money("99000"),money("1000"));
        var shipment=delivery==null ? null : new OrderDetailsResponse.DeliveryInfo(delivery,switch (delivery) {
            case "FAILED" -> "Giao hàng thất bại"; case "REDELIVERY_SCHEDULED" -> "Chờ giao lại";
            case "DELIVERED" -> "Đã giao hàng"; default -> "Đang giao";
        },null,time,delivery.equals("DELIVERED")?time.plusHours(1):null,null,
                delivery.equals("FAILED")?"Không gặp khách":null);
        return new OrderDetailsResponse.ShopGroup(id,id==20?"Vườn Xanh":"Fresh Market",
                VietFreshHub.order.service.OrderLabels.shop(state),money("10000"),money("109000"),List.of(item),
                money("100000"),money("1000"),state,shipment);
    }
    void show(String primary,List<OrderDetailsResponse.ShopGroup> shops) {
        var payments=List.of(new OrderDetailsResponse.PaymentInfo(1L,"Thanh toán khi nhận hàng","COD","PENDING","Chờ thu COD",money("381000"),null,time,null),
                new OrderDetailsResponse.PaymentInfo(2L,"Thanh toán trực tuyến","VNPAY","PAID","Đã thanh toán",money("381000"),time.plusHours(1),time,"VN-123"));
        var detail=new OrderDetailsResponse(10L,"VF-10",primary,"Chưa thu tiền","Nguyễn An","0900000000","Địa chỉ lúc đặt",
                money("380000"),money("10000"),money("11000"),money("381000"),time,shops,payments);
        when(reads.getDetails(7L,10L)).thenReturn(detail);
        when(orders.actions(7L,10L)).thenReturn(new OrderActionsResponse(false,"Đơn đã bắt đầu xử lý.","COD"));
        var groups=shops.stream().map(shop -> new OrderTrackingResponse.ShopTracking(shop.shopOrderId(),shop.shopName(),shop.statusLabel(),shop.statusCode(),null,null,null,
                List.of(new OrderTrackingResponse.Event(time,"Đã đặt hàng","INTERNAL-NOTE-SECRET","ORDER"),
                        new OrderTrackingResponse.Event(null,"Mốc chỉ suy diễn",null,"ORDER",false),
                        new OrderTrackingResponse.Event(null,"Cập nhật đã ghi nhận",null,"ORDER")))).toList();
        when(orders.tracking(7L,10L)).thenReturn(new OrderTrackingResponse(10L,"VF-10","Parent không dùng","COMPLETED",groups));
    }
    String render(String suffix) throws Exception {
        var result=mvc.perform(get("/customer/orders/10"+suffix).principal(auth)
                .requestAttr(org.springframework.security.web.csrf.CsrfToken.class.getName(),
                        new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN","_csrf","test-token")))
                .andReturn();
        assertEquals(200,result.getResponse().getStatus());return result.getResponse().getContentAsString();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "COMPLETED,OUT_FOR_DELIVERY,Vận chuyển", "COMPLETED,CANCELLED,Hoàn thành",
        "PENDING,PREPARING,Chờ xác nhận", "COMPLETED,COMPLETED,Hoàn thành", "CANCELLED,CANCELLED,Đã hủy"})
    void mixedShopStatusesRemainDistinctFromPrimaryStage(String first,String second,String primary) throws Exception {
        show(primary,List.of(shop(20L,first,null),shop(21L,second,null)));
        String html=render("");
        assertTrue(html.contains("detail-primary-stage\">"+primary));
        assertTrue(html.contains("data-shop-status=\""+first+"\""));
        assertTrue(html.contains("data-shop-status=\""+second+"\""));
        assertTrue(html.contains("381.000"));
        assertEquals(first.equals("CANCELLED")||second.equals("CANCELLED"),html.contains("Đơn có phần đã hủy"));
        assertFalse(html.contains("Parent không dùng"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FAILED","REDELIVERY_SCHEDULED","DELIVERED","OUT_FOR_DELIVERY"})
    void deliveryStatesDoNotCompleteShopOrCreateRefunds(String status) throws Exception {
        var child=shop(20L,"OUT_FOR_DELIVERY",status);
        show("Vận chuyển",List.of(child));
        String html=render("");
        assertTrue(html.contains(child.delivery().statusLabel()));
        assertTrue(html.contains("data-shop-status=\"OUT_FOR_DELIVERY\""));
        assertTrue(html.contains("detail-primary-stage\">Vận chuyển"));
        assertFalse(html.contains("Trả hàng/Hoàn tiền"));
        assertFalse(html.contains("Yêu cầu hoàn tiền"));
    }
    @Test void rendersSnapshotsEveryPaymentAndOnlyVerifiedTimelineEvents() throws Exception {
        show("Vận chuyển",List.of(shop(20L,"COMPLETED","DELIVERED"),shop(21L,"OUT_FOR_DELIVERY","FAILED")));
        String html=render("");
        for (String value:List.of("Xoài cát","Hộp 1 kg","SKU-OLD","Số lượng: 2","50.000","99.000","381.000","380.000","10.000","11.000",
                "Địa chỉ lúc đặt","Nguyễn An","0900000000","Chưa có ảnh","Chờ thu COD","Đã thanh toán","VN-123","11:00 20/10/2026","Cập nhật đã ghi nhận","Chưa ghi nhận thời gian")) assertTrue(html.contains(value),value);
        assertTrue(html.contains("data-payment-id=\"1\""));assertTrue(html.contains("data-payment-id=\"2\""));
        assertFalse(html.contains("Mốc chỉ suy diễn"));assertFalse(html.contains("INTERNAL-NOTE-SECRET"));
        assertFalse(html.contains("/customer/orders/10/cancel"));
        assertTrue(html.contains("/customer/orders/10/reorder"));
        assertTrue(html.contains("name=\"_csrf\""));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase5-detail-preview.html"),html);
    }
    @Test void confirmationRetainsRealActionsAndCancellationDecision() throws Exception {
        show("Chờ xác nhận",List.of(shop(20L,"PENDING",null)));
        when(orders.actions(7L,10L)).thenReturn(new OrderActionsResponse(true,null,"COD"));
        String html=render("/confirmation");
        assertTrue(html.contains("Đặt hàng thành công"));
        assertTrue(html.contains("/customer/orders/10/cancel"));assertTrue(html.contains("test-token"));
        assertTrue(html.contains("/customer/orders/10/tracking"));
        assertTrue(html.contains("Chưa có thông tin giao hàng"));
    }
    @Test void noShopsMeansNoTrackingOrReorderButtonsAndNoExtraTrackingQuery() throws Exception {
        show("Chờ xác nhận",List.of());String html=render("");
        assertFalse(html.contains("/customer/orders/10/tracking"));assertFalse(html.contains("/customer/orders/10/reorder"));
        verify(orders,never()).tracking(anyLong(),anyLong());
    }
    @Test void unsafeTextIsEscapedAndMissingOptionalFieldsStayAbsent() throws Exception {
        var item=new OrderDetailsResponse.Item("<script>product()</script>","Loại cũ",null,null,1,money("12345.50"),money("12345.50"));
        var child=new OrderDetailsResponse.ShopGroup(20L,"<script>shop()</script>","Chờ xác nhận",BigDecimal.ZERO,money("12345.50"),List.of(item),money("12345.50"),BigDecimal.ZERO,"PENDING",null);
        var pay=new OrderDetailsResponse.PaymentInfo(1L,"COD","COD","PENDING","Chờ thu COD",money("12345.50"),null,null," ");
        var detail=new OrderDetailsResponse(10L,"VF-10","Chờ xác nhận","Chưa thu tiền","<script>recipient()</script>","0900000000","Địa chỉ cũ",
                money("12345.50"),BigDecimal.ZERO,BigDecimal.ZERO,money("12345.50"),time,List.of(child),List.of(pay));
        when(reads.getDetails(7L,10L)).thenReturn(detail);
        when(orders.actions(7L,10L)).thenReturn(new OrderActionsResponse(false,null,"COD"));
        String html=render("");
        assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>product()"));
        assertFalse(html.contains("<script>recipient()"));assertTrue(html.contains("12.345,50"));
        assertFalse(html.contains("Mã tham chiếu thanh toán"));assertFalse(html.contains("Ghi nhận thanh toán lúc"));
        assertFalse(html.contains("SKU:"));assertTrue(html.contains("Chưa có thông tin giao hàng"));
        assertTrue(html.contains("Chưa có sự kiện được ghi nhận"));
    }
    @Test void otherCustomersOrderReturns404BeforeLoadingActionsOrTracking() throws Exception {
        when(reads.getDetails(7L,10L)).thenThrow(new VietFreshHub.exception.ResourceNotFoundException("Không tìm thấy đơn"));
        assertEquals(404,mvc.perform(get("/customer/orders/10").principal(auth)).andReturn().getResponse().getStatus());
        verifyNoInteractions(orders);
    }
    @Test void exportTrackingAndConfirmationForIntegratedUiRegression() throws Exception {
        show("Chờ xác nhận",List.of(shop(20L,"PENDING",null)));
        when(orders.actions(7L,10L)).thenReturn(new OrderActionsResponse(true,null,"COD"));
        String confirmation=render("/confirmation");
        assertTrue(confirmation.contains("Đặt hàng thành công"));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase6-confirmation-preview.html"),confirmation);
        var timeline=new OrderTrackingResponse(10L,"VF-10","Vận chuyển","PROCESSING",List.of(
                new OrderTrackingResponse.ShopTracking(20L,"Vườn Xanh","Hoàn thành","COMPLETED","Đã giao hàng","DELIVERED",null,
                        List.of(new OrderTrackingResponse.Event(time,"Giao hàng thành công",null,"DELIVERY"))),
                new OrderTrackingResponse.ShopTracking(21L,"Fresh Market","Đang giao","OUT_FOR_DELIVERY","Giao hàng thất bại","FAILED","Không gặp khách",
                        List.of(new OrderTrackingResponse.Event(time,"Giao hàng thất bại",null,"DELIVERY")))));
        when(orders.tracking(7L,10L)).thenReturn(timeline);
        var response=mvc.perform(get("/customer/orders/10/tracking").principal(auth)).andReturn().getResponse();
        assertEquals(200,response.getStatus());String tracking=response.getContentAsString();
        assertTrue(tracking.contains("Vườn Xanh"));assertTrue(tracking.contains("Fresh Market"));
        assertTrue(tracking.contains("Giao hàng thất bại"));assertFalse(tracking.contains("Trả hàng/Hoàn tiền"));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase6-tracking-preview.html"),tracking);
    }
}
