package VietFreshHub.customer;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CustomerHeaderViewTest {
    static class Expressions extends org.springframework.security.access.expression.AbstractSecurityExpressionHandler<org.springframework.security.web.FilterInvocation> {
        @Override protected org.springframework.security.access.expression.SecurityExpressionOperations createSecurityExpressionRoot(
                Authentication authentication, org.springframework.security.web.FilterInvocation invocation) {
            var root = new org.springframework.security.access.expression.SecurityExpressionRoot(authentication) {};
            root.setTrustResolver(new org.springframework.security.authentication.AuthenticationTrustResolverImpl());
            return root;
        }
    }
    public static class Notifications {
        public long countUnread(Authentication authentication) { return 0; }
    }
    @org.springframework.context.annotation.Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class Configuration {}
    @Controller static class Views {
        @GetMapping("/header") String header(Model model) {
            model.addAttribute("headerAccountName", "Nguyễn An");
            return "fragments/storefront :: header";
        }
        @GetMapping("/head") String head() { return "fragments/storefront :: head(title='Giỏ hàng')"; }
        @GetMapping("/orders-head") String ordersHead() { return "fragments/order-pages :: head(title='Đơn mua')"; }
        @GetMapping("/catalog") String catalog(@org.springframework.web.bind.annotation.RequestParam(defaultValue="14") int total,
                @org.springframework.web.bind.annotation.RequestParam(defaultValue="1") int page, Model model) {
            int count = Math.max(0, Math.min(12, total - (page - 1) * 12));
            model.addAttribute("products", List.of());
            model.addAttribute("keyword", "xoài");
            model.addAttribute("category", "trai-cay-nhap-khau");
            model.addAttribute("categories", List.of(
                    new VietFreshHub.catalog.dto.CatalogCategoryResponse("trai-cay-nhap-khau", "Trái cây nhập khẩu"),
                    new VietFreshHub.catalog.dto.CatalogCategoryResponse("qua-tang-trai-cay", "Giỏ quà trái cây")));
            model.addAttribute("productPage", new org.springframework.data.domain.PageImpl<>(
                    java.util.Collections.nCopies(count, "product"), org.springframework.data.domain.PageRequest.of(page - 1, 12), total));
            return "customer/home";
        }
    }
    @Test void sharedFragmentsRenderAccountAndCommonAssets() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Configuration.class, Views.class);
            var resolver = new SpringResourceTemplateResolver(); resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
            engine.addDialect(new SpringSecurityDialect());
            var views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            context.addBeanFactoryPostProcessor(beans -> {
                beans.registerSingleton("notificationService", new Notifications());
                beans.registerSingleton("webSecurityExpressionHandler", new Expressions());
                beans.registerSingleton("viewResolver", views);
            });
            context.refresh();
            views.setApplicationContext(context);
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var auth = new UsernamePasswordAuthenticationToken("an@example.com", null,
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            String html = mvc.perform(get("/header").principal(auth)).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("Nguyễn An"));
            assertTrue(html.contains("data-header-account"));
            assertTrue(html.contains("/customer/orders"));
            assertTrue(html.contains("/customer/addresses"));
            assertFalse(html.contains("Đăng nhập / Đăng ký"));
            assertTrue(html.contains("VIETFRESH HUB"));
            String first = mvc.perform(get("/catalog")).andReturn().getResponse().getContentAsString();
            assertTrue(first.contains("page=2"));
            assertTrue(first.contains("Trái cây nhập khẩu"));
            assertTrue(first.contains("Giỏ quà trái cây"));
            assertTrue(first.contains("category=trai-cay-nhap-khau"));
            assertTrue(first.contains("name=\"category\""));
            assertTrue(first.contains("aria-current=\"page\">1</span>"));
            assertFalse(first.contains("customer-taskbar"));
            String second = mvc.perform(get("/catalog").param("page", "2")).andReturn().getResponse().getContentAsString();
            assertTrue(second.contains("aria-current=\"page\">2</span>"));
            assertTrue(second.contains("page=1"));
            for (String total : List.of("0", "1", "12")) {
                String single = mvc.perform(get("/catalog").param("total", total)).andReturn().getResponse().getContentAsString();
                assertFalse(single.contains("catalog-pagination"));
            }
            for (String url : List.of("/head", "/orders-head")) {
                String head = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
                assertTrue(head.contains("tailwind-config"));
                assertTrue(head.contains("/css/customer-shell.css"));
                assertTrue(head.contains("/css/storefront.css"));
            }
            SecurityContextHolder.clearContext();
            SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.AnonymousAuthenticationToken("test", "anonymousUser",
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            String guest = mvc.perform(get("/header")).andReturn().getResponse().getContentAsString();
            assertFalse(guest.contains("data-header-account"));
            assertTrue(guest.contains("/login"));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
