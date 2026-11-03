package VietFreshHub.cart;
import VietFreshHub.cart.controller.CustomerCartViewController;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.cart.dto.*;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.recommendation.service.ProductRecommendationService;

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
class CartScreenRenderTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CartService cart = mock(CartService.class);
    final CatalogService catalog = mock(CatalogService.class);
    final Authentication auth = mock(Authentication.class);
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() {
        when(current.requireCustomerId(auth)).thenReturn(7L);
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(RenderConfiguration.class, CustomerCartViewController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("currentCustomer", current);
            beans.registerSingleton("cart", cart);
            beans.registerSingleton("catalog", catalog);
            beans.registerSingleton("recommendations", mock(ProductRecommendationService.class));
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
    @Test void rendersRealCartStatesAndKeepsCheckoutSelection() throws Exception {
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")).when(auth).getAuthorities();
        var item = CartItemDto.builder().cartItemId(9L).variantId(12L).shopId(2L).shopName("Shop").productName("Táo").variantName("1 kg").quantity(2).unitPriceSnapshot(new BigDecimal("89000")).itemSubtotal(new BigDecimal("178000")).availableStock(5).purchasable(true).quantityEditable(true).imageUrl("/images/apple.jpg").build();
        when(cart.getActiveCart(7L)).thenReturn(CartDto.builder().cartId(3L).items(List.of(item)).totalAmount(new BigDecimal("178000")).build());
        when(catalog.listProducts(1)).thenReturn(Page.empty());
        var response = mvc.perform(get("/cart").principal(auth)).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        String html=response.getContentAsString();
        assertTrue(html.contains("178.000")); assertFalse(html.contains("Sắp có"));
        assertFalse(html.contains("Voucher nền tảng"));
        assertFalse(html.contains("Mã giảm giá của shop"));
        assertFalse(html.contains("Giảm giá / voucher"));
        assertFalse(html.contains("Tính khi thanh toán"));
        assertTrue(html.contains("Mua hàng (1)"));
        assertTrue(html.contains("name=\"itemIds\"")); assertTrue(html.contains("/customer/checkout"));
        assertTrue(html.contains("/cart/update")); assertTrue(html.contains("/cart/remove"));
        assertFalse(html.contains("15.000"));
    }

    @Test void rendersIndependentShopsAndUnavailableItems() throws Exception {
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")).when(auth).getAuthorities();
        var apple = CartItemDto.builder().cartItemId(9L).variantId(12L).shopId(2L).shopName("Vườn trái cây Miền Tây").productName("Táo Gala New Zealand").variantName("Khay 4 quả").quantity(2).unitPriceSnapshot(new BigDecimal("89000")).itemSubtotal(new BigDecimal("178000")).availableStock(5).purchasable(true).quantityEditable(true).imageUrl("/images/apple.jpg").build();
        var mango = CartItemDto.builder().cartItemId(10L).variantId(13L).shopId(2L).shopName("Vườn trái cây Miền Tây").productName("Xoài cát Hòa Lộc").variantName("Hộp 1 kg").quantity(1).unitPriceSnapshot(new BigDecimal("65000")).itemSubtotal(new BigDecimal("65000")).availableStock(3).purchasable(true).quantityEditable(true).imageUrl("/images/xoai-cat.png").build();
        var pomelo = CartItemDto.builder().cartItemId(11L).variantId(14L).shopId(3L).shopName("Nông sản Vĩnh Long").productName("Bưởi da xanh").variantName("Quả 1,2 kg").quantity(1).unitPriceSnapshot(new BigDecimal("52000")).itemSubtotal(new BigDecimal("52000")).availableStock(0).purchasable(false).quantityEditable(false).availabilityMessage("Tạm hết hàng.").imageUrl("/images/buoi-da-xanh.jpg").build();
        when(cart.getActiveCart(7L)).thenReturn(CartDto.builder().cartId(3L).items(List.of(apple, mango, pomelo)).totalAmount(new BigDecimal("243000")).hasUnavailableItems(true).build());
        when(catalog.listProducts(1)).thenReturn(Page.empty());
        var response = mvc.perform(get("/cart").principal(auth)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String html = response.getContentAsString();
        assertTrue(html.contains("/shops/2/products"));
        assertTrue(html.contains("/shops/3/products"));
        assertEquals(2, html.split("class=\"shop-selection-checkbox\"", -1).length - 1);
        assertTrue(html.contains("Mua hàng (2)"));
        assertTrue(html.contains("243.000"));
        assertTrue(html.contains("Tạm hết hàng."));
        assertFalse(html.contains("cart-suggestion-add"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/cart-redesign-preview.html"), html);
    }

    @Test void rendersEmptyCartWithoutCheckoutButton() throws Exception {
        when(catalog.listProducts(1)).thenReturn(Page.empty());
        var response = mvc.perform(get("/cart")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String html = response.getContentAsString();
        assertTrue(html.contains("Giỏ hàng của bạn đang trống"));
        assertTrue(html.contains("Khám phá sản phẩm"));
        assertFalse(html.contains("id=\"checkout-selected-button\""));
    }
}
