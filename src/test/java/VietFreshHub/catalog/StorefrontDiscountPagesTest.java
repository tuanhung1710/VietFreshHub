package VietFreshHub.catalog;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.payment.vnpay.VnpayPaymentTimeoutScheduler;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.service.ShopBrowseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storefront-discount-pages;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=none", "inventory.alerts.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class StorefrontDiscountPagesTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogService catalog;
    @MockitoBean StorefrontService storefront;
    @MockitoBean CartService carts;
    @MockitoBean ShopBrowseService shops;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean VietFreshHub.notification.service.NotificationService notifications;
    @MockitoBean VnpayPaymentTimeoutScheduler paymentScheduler;
    @MockitoBean VietFreshHub.vietmap.service.VietMapService vietMap;
    @MockitoBean VietFreshHub.payment.vnpay.VnpayQueryClient vnpayQueryClient;

    @BeforeEach
    void setUp() {
        var shop = new Shop();
        shop.setShopId(1L);
        shop.setShopName("Vườn trái cây Sơn");
        when(shops.profile(1L)).thenReturn(shop);
        when(storefront.shopAddress(1L)).thenReturn(Optional.empty());
        when(storefront.productDetails(anyLong())).thenReturn(
                new VietFreshHub.catalog.repository.CatalogDetailRepository.Details(List.of(), 0, List.of()));
        var variant = CatalogProductResponse.VariantResponse.builder().variantId(3L).variantName("Hộp 1 kg")
                .price(new BigDecimal("65000")).basePrice(new BigDecimal("80000"))
                .compareAtPrice(new BigDecimal("100000")).availableStock(20).purchasable(true).build();
        var product = CatalogProductResponse.builder().productId(1L).shopId(1L).name("Xoài")
                .imageUrl("/images/xoai-cat.png").variants(List.of(variant)).selectedVariant(variant).build();
        var related = CatalogProductResponse.builder().productId(2L).shopId(1L).name("Bưởi")
                .imageUrl("/images/buoi-da-xanh.jpg").variants(List.of(variant)).selectedVariant(variant).build();
        when(catalog.getProduct(any(), any(), any())).thenReturn(product);
        when(catalog.listProducts(any(VietFreshHub.catalog.dto.CatalogBrowseFilter.class)))
                .thenReturn(new PageImpl<>(List.of(product, related)));
        var user = new User();
        user.setUserId(5L);
        user.setEmail("customer@example.com");
        user.setFullName("Khách hàng");
        user.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(roles.findRoleNamesByUserId(5L)).thenReturn(List.of("ROLE_CUSTOMER"));
    }

    @Test
    void guestsSeeTheReferencePriceAndDiscountOnProductDetailsAndInThePublicApi() throws Exception {
        assertProductDiscountIsRendered();
        var product = catalog.getProduct(null, 1L, null);
        when(storefront.products(any())).thenReturn(new VietFreshHub.common.PageResponse<>(
                List.of(VietFreshHub.catalog.dto.StorefrontProduct.from(product)), 1, 10, 1, 1));
        mvc.perform(get("/storefront/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].price").value(65000))
                .andExpect(jsonPath("$.items[0].compareAtPrice").value(100000))
                .andExpect(jsonPath("$.items[0].imageUrl").value("/images/xoai-cat.png"))
                .andExpect(jsonPath("$.items[0].discountPercent").value(35));
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void customersSeeTheSameProductDiscountAsGuests() throws Exception {
        assertProductDiscountIsRendered();
    }

    private void assertProductDiscountIsRendered() throws Exception {
        mvc.perform(get("/product-detail/3")).andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*id=\"currentPrice\"[^>]*>65\\.000 VND</div>.*")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"compareAtPrice\"(?![^>]*hidden)[^>]*>100\\.000 VND</del>.*")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"discountPercent\"(?![^>]*hidden)[^>]*>-35%</span>.*")))
                .andExpect(content().string(containsString("data-compare-at-price=\"100000\"")))
                .andExpect(content().string(containsString("data-discount-percent=\"35\"")))
                .andExpect(content().string(matchesPattern("(?s).*class=\"related\".*class=\"productphoto\">.*class=\"discount-badge discount-badge--image\"[^>]*>-35%</span>\\s*</div>.*")))
                .andExpect(content().string(not(matchesPattern("(?s).*class=\"related\".*<del.*"))));
    }

    @Test
    void productsWithoutAReferencePriceHideTheOldPriceAndDiscount() throws Exception {
        var variant = CatalogProductResponse.VariantResponse.builder().variantId(3L).variantName("Hộp 1 kg")
                .price(new BigDecimal("65000")).availableStock(20).build();
        var product = CatalogProductResponse.builder().productId(1L).shopId(1L).name("Xoài")
                .variants(List.of(variant)).selectedVariant(variant).build();
        when(catalog.getProduct(any(), any(), any())).thenReturn(product);
        mvc.perform(get("/product-detail/3")).andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*id=\"compareAtPrice\"[^>]*hidden[^>]*></del>.*")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"discountPercent\"[^>]*hidden[^>]*></span>.*")));
    }

}
