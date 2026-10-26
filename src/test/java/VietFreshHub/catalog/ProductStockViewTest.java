package VietFreshHub.catalog;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.controller.CustomerCartViewController;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.recommendation.service.ProductRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ProductStockViewTest {
    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    static class RenderConfiguration {
        @Bean org.springframework.web.servlet.support.RequestDataValueProcessor requestDataValueProcessor() {
            return new org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor();
        }
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

    @Test void rendersFullDetailWithSoldOutOptionAndWritesBrowserFixture() throws Exception {
        var current = mock(CurrentCustomerService.class);
        var catalog = mock(CatalogService.class);
        var carts = mock(CartService.class);
        var auth = new UsernamePasswordAuthenticationToken("stock@test.invalid", "unused",
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
        when(current.requireCustomerId(auth)).thenReturn(7L);
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().totalItems(0).build());
        var first = CatalogProductResponse.VariantResponse.builder().variantId(11L).variantName("Hộp 1 kg")
                .price(new BigDecimal("30000")).imageUrl("/images/fruit-1.jpg").availableStock(5)
                .purchasable(true).availabilityMessage("Còn 5 sản phẩm có thể mua.").build();
        var second = CatalogProductResponse.VariantResponse.builder().variantId(12L).variantName("Hộp 2 kg")
                .price(new BigDecimal("50000")).imageUrl("/images/fruit-2.jpg").availableStock(0)
                .purchasable(false).availabilityMessage("Tạm hết hàng.").build();
        var product = CatalogProductResponse.builder().productId(1L).name("Xoài tươi")
                .shopName("Cửa hàng trái cây").imageUrl(first.getImageUrl()).description("Trái cây tươi mỗi ngày.")
                .variants(List.of(first, second)).selectedVariant(first).build();
        when(catalog.getProduct(11L, null, null)).thenReturn(product);
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(RenderConfiguration.class, CustomerCartViewController.class);
            context.addBeanFactoryPostProcessor(beans -> {
                beans.registerSingleton("currentCustomer", current);
                beans.registerSingleton("carts", carts);
                beans.registerSingleton("catalog", catalog);
                beans.registerSingleton("recommendations", mock(ProductRecommendationService.class));
                beans.registerSingleton("notificationService", mock(VietFreshHub.notification.service.NotificationService.class));
            });
            SecurityContextHolder.getContext().setAuthentication(auth);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            String html = mvc.perform(get("/product-detail/11").principal(auth).requestAttr(
                            org.springframework.security.web.csrf.CsrfToken.class.getName(),
                            new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "stock-test-token")))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("data-product-stock"));
            assertTrue(html.contains("/api/catalog/products/1/availability"));
            assertTrue(html.contains("data-variant-id=\"12\""));
            assertTrue(html.contains("/js/product-stock.js"));
            assertTrue(html.contains("name=\"_csrf\""));
            Path output = Path.of("target", "stock-ux"); Files.createDirectories(output);
            Files.writeString(output.resolve("product-detail.html"), html);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
