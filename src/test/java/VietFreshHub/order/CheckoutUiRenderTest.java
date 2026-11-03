package VietFreshHub.order;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.cart.dto.*;

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
            model.addAttribute("availableCoupons", List.of());
            model.addAttribute("couponApplications", List.of());model.addAttribute("couponErrors", java.util.Map.of());
            model.addAttribute("loyaltyBalance", new VietFreshHub.loyalty.dto.LoyaltyBalanceResponse(1L,7L,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null));
            model.addAttribute("couponDiscount",BigDecimal.ZERO);model.addAttribute("loyaltyDiscount",BigDecimal.ZERO);
            model.addAttribute("cart",cart);model.addAttribute("checkout",preview);model.addAttribute("form",form);model.addAttribute("shopGroups",java.util.Map.of(1L,cart.getItems()));return "customer/checkout";
        }

        @org.springframework.web.bind.annotation.GetMapping("/preview/ready")
        String ready(org.springframework.ui.Model model) {
            preview(model);
            var cart = (CartDto) model.getAttribute("cart");
            var first = cart.getItems().getFirst();
            first.setImageUrl("/images/products/apple-photo.png");
            var second = CartItemDto.builder().cartItemId(14L).variantId(3L).shopId(2L).shopName("Nông sản Vĩnh Long").productName("Bưởi da xanh").variantName("Quả 1,2 kg").quantity(2).unitPriceSnapshot(new BigDecimal("52000")).itemSubtotal(new BigDecimal("104000")).availableStock(5).purchasable(true).imageUrl("/images/buoi-da-xanh.jpg").build();
            cart.setItems(List.of(first, second));
            cart.setTotalAmount(new BigDecimal("163000"));
            var addresses = List.of(new CheckoutPreview.AddressOption(1L,"Trần Quốc Bảo","0987253641","18 Trần Hưng Đạo, Phường Bến Thành, Thành phố Hồ Chí Minh",true,true), new CheckoutPreview.AddressOption(2L,"Trần Quốc Bảo","0987253641","24 Nguyễn Huệ, Thành phố Hồ Chí Minh",false,true));
            var quotes = java.util.Map.of(1L, new ShopShippingQuote(1L, null, null, null, null, new BigDecimal("3000"), new BigDecimal("15000")), 2L, new ShopShippingQuote(2L, null, null, null, null, new BigDecimal("4000"), new BigDecimal("20000")));
            model.addAttribute("checkout", new CheckoutPreview(cart, addresses, 1L, quotes, new BigDecimal("35000"), new BigDecimal("198000"), true, true, "fingerprint", null, false));
            model.addAttribute("shopGroups", java.util.Map.of(1L,List.of(first),2L,List.of(second)));
            var form = (CheckoutRequest) model.getAttribute("form");
            form.setCouponCodes(List.of("VUON10"));
            form.setRewardAmountToUse(new BigDecimal("3000"));
            var coupon = new VietFreshHub.coupon.dto.CouponApplicationResponse(1L,"VUON10",1L,1L,new BigDecimal("59000"),new BigDecimal("10000"),new BigDecimal("49000"));
            model.addAttribute("availableCoupons",List.of(coupon));
            model.addAttribute("couponApplications",List.of(coupon));model.addAttribute("couponErrors",java.util.Map.of());
            model.addAttribute("couponDiscount",new BigDecimal("10000"));
            model.addAttribute("loyaltyBalance",new VietFreshHub.loyalty.dto.LoyaltyBalanceResponse(1L,7L,new BigDecimal("8330"),new BigDecimal("8330"),BigDecimal.ZERO,null));
            model.addAttribute("redemption",new VietFreshHub.loyalty.dto.LoyaltyRedemptionResponse(new BigDecimal("3000"),new BigDecimal("3000"),new BigDecimal("5330")));
            model.addAttribute("loyaltyDiscount",new BigDecimal("3000"));
            model.addAttribute("checkoutTotal",new BigDecimal("185000"));
            return "customer/checkout";
        }

        @org.springframework.web.bind.annotation.GetMapping("/preview/reward-error")
        String rewardError(org.springframework.ui.Model model) {
            preview(model);
            ((CheckoutRequest) model.getAttribute("form")).setRewardAmountToUse(new BigDecimal("1000"));
            model.addAttribute("loyaltyError", "Số tiền thưởng vượt quá số dư hiện có.");
            return "customer/checkout";
        }
    }
    @Test void rendersCheckoutAndKeepsOutOfRangeCheckoutDisabled() throws Exception {
        var response=mvc.perform(get("/preview")).andReturn().getResponse();assertEquals(200,response.getStatus());String html=response.getContentAsString();
        for(String value:List.of("checkout-page.css","checkout-address-panel","checkout-payment-panel","checkout-products-panel","59.000","Không có phương thức vận chuyển phù hợp","name=\"checkoutToken\"","name=\"addressId\"","name=\"paymentMethodCode\"","action=\"/customer/checkout\""))assertTrue(html.contains(value),value);
        var confirm=html.substring(html.lastIndexOf("<button"),html.indexOf("</button>",html.lastIndexOf("<button")));assertTrue(confirm.contains("disabled=\"disabled\""));assertTrue(confirm.contains("data-confirm-order"));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/ui-preview"));java.nio.file.Files.writeString(java.nio.file.Path.of("target/ui-preview/checkout.html"),html);
    }

    @Test void rendersMultiShopSummaryWithExistingCouponAndRewardBindings() throws Exception {
        var response = mvc.perform(get("/preview/ready")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String html = response.getContentAsString();
        for (String value : List.of("Mã giảm giá của shop", "Không sử dụng mã giảm giá", "VUON10", "Nông sản Vĩnh Long", "185.000", "8.330", "Tiền thưởng đã sử dụng", "name=\"couponCodes\"", "name=\"rewardAmountInput\"", "/customer/checkout/coupon/preview", "/customer/checkout/reward/preview", "/customer/checkout/address/1/edit", "/customer/checkout/address/new", "/customer/addresses")) {
            assertTrue(html.contains(value), value);
        }
        assertTrue(html.contains("<select id=\"coupon-1\""));
        assertFalse(html.contains("Không dùng coupon"));
        var confirm = html.substring(html.lastIndexOf("<button"), html.indexOf("</button>", html.lastIndexOf("<button")));
        assertFalse(confirm.contains("disabled="));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/ui-preview"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/ui-preview/checkout-ready.html"), html);
    }

    @Test void leavesRewardInputEditableToClearRejectedAmountWithZeroBalance() throws Exception {
        var response = mvc.perform(get("/preview/reward-error")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String html = response.getContentAsString();
        int start = html.indexOf("<input id=\"reward-amount-input\"");
        assertTrue(start >= 0);
        assertFalse(html.substring(start, html.indexOf('>', start)).contains("disabled="));
        assertTrue(html.contains("Số tiền thưởng vượt quá số dư hiện có."));
    }
}
