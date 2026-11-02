package VietFreshHub.catalog;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.payment.vnpay.VnpayPaymentTimeoutScheduler;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopOfferRepository;
import VietFreshHub.shop.repository.ShopReviewRepository;
import VietFreshHub.shop.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-pages;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=none", "inventory.alerts.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class CustomerShopPagesTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogService catalog;
    @MockitoBean VietFreshHub.catalog.service.StorefrontService storefront;
    @MockitoBean CartService carts;
    @MockitoBean ShopBrowseService shops;
    @MockitoBean ShopReviewService reviews;
    @MockitoBean ShopOfferService offers;
    @MockitoBean ShopFavoriteService favorites;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean VietFreshHub.notification.service.NotificationService notifications;

    @MockitoBean VnpayPaymentTimeoutScheduler paymentScheduler;
    @MockitoBean VietFreshHub.vietmap.service.VietMapService vietMap;
    @MockitoBean VietFreshHub.payment.vnpay.VnpayQueryClient vnpayQueryClient;

    @BeforeEach
    void setUp() {
        Shop shop = new Shop();
        shop.setShopId(1L);
        shop.setShopName("Vườn trái cây Sơn");
        shop.setDescription("Trái cây theo mùa, chọn từ những nhà vườn quen thuộc.");
        shop.setPhone("0901234567");
        shop.setEmail("vuonson@example.com");
        when(shops.profile(1L)).thenReturn(shop);
        when(shops.location(1L)).thenReturn(Optional.empty());
        when(storefront.shopAddress(1L)).thenReturn(Optional.empty());
        when(storefront.productDetails(anyLong())).thenReturn(new VietFreshHub.catalog.repository.CatalogDetailRepository.Details(List.of(),0,List.of()));
        when(shops.list(anyInt(), anyString())).thenReturn(new PageImpl<>(List.of(shop), PageRequest.of(0, 12), 1));
        var variant = CatalogProductResponse.VariantResponse.builder().variantId(3L).variantName("Hộp 1 kg")
                .price(new BigDecimal("65000")).basePrice(new BigDecimal("80000")).availableStock(20).purchasable(true).build();
        var products = IntStream.range(0, 8).mapToObj(i -> CatalogProductResponse.builder().productId((long) i + 1)
                .shopId(1L).shopName(shop.getShopName()).name(i % 2 == 0 ? "Xoài Cát Hòa Lộc" : "Bưởi da xanh")
                .description("Trái cây tươi theo mùa.").imageUrl(i % 2 == 0 ? "/images/xoai-cat.png" : "/images/buoi-da-xanh.jpg")
                .variants(List.of(variant)).selectedVariant(variant).build()).toList();
        when(catalog.categories()).thenReturn(List.of());
        when(catalog.listProducts(any(VietFreshHub.catalog.dto.CatalogBrowseFilter.class)))
                .thenReturn(new PageImpl<>(products, PageRequest.of(0, 12), 25));
        when(catalog.getProduct(any(), any(), any())).thenReturn(products.getFirst());
        when(reviews.summary(1L)).thenReturn(new ShopReviewRepository.Summary(12L, new BigDecimal("4.8")));
        when(reviews.list(eq(1L), any(), anyInt())).thenReturn(new PageImpl<>(List.of(
                new ShopReviewRepository.Review(5, "Trái cây tươi, đóng gói cẩn thận.", "Minh Anh", LocalDateTime.of(2026, 11, 1, 9, 30)))));
        when(offers.list(eq(1L), anyInt())).thenReturn(new PageImpl<>(List.of(new ShopOfferRepository.Offer(
                "Ưu đãi đầu tháng", "Dành cho đơn hàng trái cây tại cửa hàng.", "VUONSON10", "PERCENT",
                BigDecimal.TEN, new BigDecimal("50000"), new BigDecimal("200000"), 1, LocalDateTime.of(2026, 11, 15, 23, 59)))));
        User user = new User();
        user.setUserId(5L);
        user.setEmail("customer@example.com");
        user.setFullName("Trần Quốc Bảo");
        user.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(roles.findRoleNamesByUserId(5L)).thenReturn(List.of("ROLE_CUSTOMER"));
        when(carts.getActiveCart(5L)).thenReturn(VietFreshHub.cart.dto.CartDto.builder()
                .userId(5L).totalItems(0).totalAmount(BigDecimal.ZERO).build());
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void sharedAccountMenuUsesTheCustomersNameAcrossAllHeaderLayouts() throws Exception {
        String[][] pages = {{"/home", "home"}, {"/shops/1", "shop"}, {"/product-detail/3", "product"},
                {"/cart", "cart"}, {"/customer/profile", "profile"}};
        for (String[] page : pages) {
            var response = mvc.perform(get(page[0])).andExpect(status().isOk())
                    .andExpect(content().string(containsString("/css/header-account.css")))
                    .andExpect(content().string(containsString("/js/header-account.js")))
                    .andExpect(content().string(containsString("Trần Quốc Bảo")))
                    .andExpect(content().string(containsString("data-header-account")))
                    .andExpect(content().string(containsString("href=\"/customer/addresses\"")))
                    .andExpect(content().string(containsString("href=\"/customer/chat\"")))
                    .andExpect(content().string(containsString("href=\"/customer/payments\"")))
                    .andExpect(content().string(containsString("action=\"/logout\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andReturn().getResponse();
            var directory = Path.of("target/account-menu-preview");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(page[1] + ".html"), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void guestAccountLinksDoNotShowTheAuthenticatedMenu() throws Exception {
        mvc.perform(get("/home")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("href=\"/register\"")))
                .andExpect(content().string(not(containsString("data-header-account"))))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void accountNameIsEscapedWhenRenderedInTheMenu() throws Exception {
        var user = users.findByEmailIgnoreCase("customer@example.com").orElseThrow();
        user.setFullName("<script>alert('name')</script>");
        mvc.perform(get("/home")).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('name')</script>"))));
    }

    @Test
    void guestsCanBrowseAllPublicPagesWithTheTemplateLayout() throws Exception {
        String[][] paths = {{"/home", "catalog"}, {"/product-detail/3", "product"}, {"/shops", "shops"},
                {"/shops/1", "shop"}, {"/shops/1/reviews", "reviews"}, {"/shops/1/offers", "offers"}};
        for (String[] entry : paths) {
            var response = mvc.perform(get(entry[0])).andExpect(status().isOk())
                    .andExpect(content().string(containsString(entry[0].startsWith("/product-detail") ? "/css/product-detail.css"
                            : entry[0].matches("/shops/\\d+") ? "/css/shop-view.css" : "/css/customer-shop.css")))
                    .andExpect(content().string(not(containsString("Chưa nhận đơn"))))
                    .andExpect(content().string(not(containsString("Chưa khả dụng"))))
                    .andReturn().getResponse();
            Path directory = Path.of("target/customer-shop-preview");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(entry[1] + ".html"), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void shopInformationUsesFormattedAddressAndTheShopJoiningDate() throws Exception {
        var location = new VietFreshHub.shop.entity.ShopLocation(1L);
        location.updateLocation(null, "Tỉnh không dùng để thay địa chỉ", null, null, null, null,
                "Địa chỉ tách riêng không dùng", "126 Lũy Bán Bích, Phường Tân Phú, TP Hồ Chí Minh", null, null);
        when(storefront.shopAddress(1L)).thenReturn(Optional.of(location.getFormattedAddress()));
        shops.profile(1L).setCreatedAt(LocalDateTime.of(2025, 3, 11, 19, 0));
        mvc.perform(get("/shops/1")).andExpect(status().isOk()).andExpect(view().name("shop/detail"))
                .andExpect(content().string(containsString("126 Lũy Bán Bích, Phường Tân Phú, TP Hồ Chí Minh")))
                .andExpect(content().string(containsString("12/03/2025")))
                .andExpect(content().string(not(containsString(location.getAddressLine()))))
                .andExpect(content().string(not(containsString(location.getProvince()))));
        mvc.perform(get("/shops/1/products")).andExpect(redirectedUrl("/shops/1?tab=all"));
    }

    @Test
    void missingFormattedAddressDoesNotFallBackToSeparateAddressComponents() throws Exception {
        var location = new VietFreshHub.shop.entity.ShopLocation(1L);
        location.updateLocation(null, "Không lấy tỉnh làm địa chỉ", null, null, null, null,
                "Không ghép địa chỉ này", null, null, null);
        when(shops.location(1L)).thenReturn(Optional.of(location));
        mvc.perform(get("/shops/1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa cập nhật địa chỉ")))
                .andExpect(content().string(not(containsString(location.getAddressLine()))))
                .andExpect(content().string(not(containsString(location.getProvince()))));
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void productDetailsRenderTheRealPurchaseFormAndOnlyRelatedProductsFromThisShop() throws Exception {
        mvc.perform(get("/product-detail/3")).andExpect(status().isOk())
                .andExpect(view().name("customer/product-detail"))
                .andExpect(content().string(containsString("action=\"/cart/add\"")))
                .andExpect(content().string(containsString("name=\"variantId\"")))
                .andExpect(content().string(containsString("name=\"quantity\"")))
                .andExpect(content().string(containsString("value=\"checkout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        verify(catalog).listProducts(argThat((VietFreshHub.catalog.dto.CatalogBrowseFilter filter) -> Long.valueOf(1L).equals(filter.getShopId())));
    }

    @Test
    void invalidFilterDoesNotInvokeCatalogQuery() throws Exception {
        mvc.perform(get("/storefront/api/products").param("minPrice", "90000").param("maxPrice", "10000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/storefront/api/products").param("pageSize", "13")).andExpect(status().isBadRequest());
        verify(storefront, never()).products(any());
    }

    @Test
    void publicApiReturnsLivePageMetadataAndGuestCartStartsAtZero() throws Exception {
        when(storefront.products(any())).thenReturn(new VietFreshHub.common.PageResponse<>(List.of(),1,10,0,1));
        mvc.perform(get("/storefront/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pages").value(1));
        mvc.perform(get("/storefront/api/cart")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(post("/storefront/api/cart").with(csrf()).contentType("application/json").content("{\"variantId\":3,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void favoriteMutationRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/shops/1/favorite").param("favorite", "true")).andExpect(status().isForbidden());
        mvc.perform(post("/shops/1/favorite").with(csrf()).param("favorite", "true")).andExpect(status().is3xxRedirection());
        verify(favorites, never()).setFavorite(any(), anyLong(), anyBoolean());
    }

    @Test
    void obsoleteProductLinksReturn404AndOfferOtherProducts() throws Exception {
        when(catalog.getProduct(any(), any(), any())).thenThrow(new VietFreshHub.exception.ResourceNotFoundException("Sản phẩm không còn được bán."));
        mvc.perform(get("/product-detail/3")).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Khám phá sản phẩm khác")));
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void customersCanSaveTheirShopAndSeeTheSavedList() throws Exception {
        mvc.perform(post("/shops/1/favorite").with(csrf()).param("favorite", "true"))
                .andExpect(redirectedUrl("/shops/1"));
        verify(favorites).setFavorite(any(), eq(1L), eq(true));
        var savedShop = shops.profile(1L);
        when(favorites.list(any(), eq(1))).thenReturn(new PageImpl<>(List.of(savedShop)));
        mvc.perform(get("/customer/favorite-shops")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cửa hàng đã lưu")));
    }
}
