package VietFreshHub.recommendation;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.controller.CustomerCartViewController;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.recommendation.dto.ProductRecommendation;
import VietFreshHub.recommendation.service.ProductRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ProductRecommendationViewTest {
    @Test void controllerUsesResolvedProductIdRatherThanVariantId() {
        var catalog = mock(CatalogService.class); var recommendations = mock(ProductRecommendationService.class);
        when(catalog.getProduct(99L, null, null)).thenReturn(CatalogProductResponse.builder().productId(1L).variants(List.of()).build());
        var controller = new CustomerCartViewController(mock(CartService.class), catalog, mock(CurrentCustomerService.class), recommendations);
        var model = new ExtendedModelMap();
        assertEquals("customer/product-detail", controller.showProductDetail(99L, null, null, null, model));
        verify(recommendations).relatedTo(1L); assertTrue(model.containsAttribute("recommendations"));
    }
    @Controller static class FragmentController {
        final List<ProductRecommendation> cards;
        FragmentController(List<ProductRecommendation> cards) { this.cards=cards; }
        @GetMapping("/fragment") public String fragment(Model model) {
            model.addAttribute("recommendations", cards); return "customer/fragments/recommendations";
        }
    }
    String render(List<ProductRecommendation> cards) throws Exception {
        try (var context = new StaticWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.refresh();
            var resolver = new SpringResourceTemplateResolver(); resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
            var views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            return MockMvcBuilders.standaloneSetup(new FragmentController(cards)).setViewResolvers(views).build()
                    .perform(get("/fragment")).andReturn().getResponse().getContentAsString();
        }
    }
    @Test void emptyRecommendationsHideSection() throws Exception {
        assertFalse(render(List.of()).contains("id=\"recommendation-heading\""));
    }
    @Test void rendersVariantLinkEscapedNameAndDecimalPriceWithoutPopularityClaim() throws Exception {
        String html = render(List.of(new ProductRecommendation(1L, 99L, "<script>x</script>", "500g", "Shop", null, new BigDecimal("12345.50"))));
        assertTrue(html.contains("/product-detail/99")); assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>x</script>")); assertTrue(html.contains("12.345,50"));
        assertTrue(html.contains("Chưa có ảnh")); assertFalse(html.contains("phổ biến"));
    }
}
