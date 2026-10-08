package VietFreshHub.Cart.controller;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.dto.*;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.config.SecurityConfig;
import VietFreshHub.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CartController.class,CustomerCartViewController.class})
@Import(SecurityConfig.class)
class CartControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CartService carts;
    @MockitoBean CurrentCustomerService customer;
    @MockitoBean CatalogService catalog;
    @MockitoBean VietFreshHub.Checkout.port.ShippingQuotePort shipping;
    @BeforeEach void setup() {
        when(customer.requireCustomerId(any())).thenReturn(7L);
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().userId(7L).totalItems(0).totalAmount(BigDecimal.ZERO).build());
    }
    @Test void cartRequiresLogin() throws Exception { mvc.perform(get("/cart")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login")); verifyNoInteractions(carts); }
    @Test void apiRequiresLogin() throws Exception { mvc.perform(get("/api/carts/active")).andExpect(status().isUnauthorized()); verifyNoInteractions(carts); }
    @Test void otherRoleCannotReadCart() throws Exception { mvc.perform(get("/cart").with(user("manager").roles("STORE_MANAGER"))).andExpect(status().isForbidden()); verifyNoInteractions(carts); }
    @Test void csrfRequiredForApi() throws Exception {
        mvc.perform(post("/api/carts/items").with(user("customer").roles("CUSTOMER")).contentType("application/json")
                .content("{\"variantId\":10,\"quantity\":2}")).andExpect(status().isForbidden());
        verify(carts,never()).addToCart(anyLong(),any());
    }
    @Test void clientHeaderCannotSelectAnotherUser() throws Exception {
        when(carts.addToCart(eq(7L),any())).thenReturn(AddToCartResponse.builder().quantity(2).message("Đã thêm sản phẩm vào giỏ hàng.").build());
        mvc.perform(post("/api/carts/items").with(user("buyer@example.com").roles("CUSTOMER")).with(csrf())
                .header("X-User-Id","999").contentType("application/json").content("{\"variantId\":10,\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(2));
        verify(carts).addToCart(eq(7L),any()); verify(carts,never()).addToCart(eq(999L),any());
    }
    @Test void invalidApiQuantityRejected() throws Exception {
        mvc.perform(post("/api/carts/items").with(user("customer").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content("{\"variantId\":10,\"quantity\":-1}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_INPUT"));
        verify(carts,never()).addToCart(anyLong(),any());
    }
    @Test void apiMissingProductReturnsJson404() throws Exception {
        when(carts.addToCart(eq(7L),any())).thenThrow(new ResourceNotFoundException("Sản phẩm không còn được bán."));
        mvc.perform(post("/api/carts/items").with(user("customer").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content("{\"variantId\":10,\"quantity\":1}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
    @Test void mvcUsesDtoValidation() throws Exception {
        mvc.perform(post("/cart/add").with(user("customer").roles("CUSTOMER")).with(csrf()).param("variantId","10").param("quantity","-1"))
                .andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("errorMessage"));
        verify(carts,never()).addToCart(anyLong(),any());
    }
    @Test void nonNumericQuantityShowsVietnameseMessage() throws Exception {
        mvc.perform(post("/cart/add").with(user("customer").roles("CUSTOMER")).with(csrf()).param("variantId","10").param("quantity","invalid"))
                .andExpect(redirectedUrl("/cart")).andExpect(flash().attribute("errorMessage", "Vui lòng nhập mã sản phẩm và số lượng là số nguyên hợp lệ."));
        verify(carts,never()).addToCart(anyLong(),any());
    }
    @Test void clearUsesAuthenticatedCustomer() throws Exception {
        mvc.perform(post("/cart/clear").with(user("customer").roles("CUSTOMER")).with(csrf()))
                .andExpect(redirectedUrl("/cart")); verify(carts).clearCart(7L);
    }
    @Test void negativeUpdateDoesNotRemoveItem() throws Exception {
        mvc.perform(post("/cart/update").with(user("customer").roles("CUSTOMER")).with(csrf()).param("variantId","10").param("quantity","-1"))
                .andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("errorMessage"));
        verify(carts,never()).updateCartItemQuantity(anyLong(),anyLong(),anyInt()); verify(carts,never()).removeCartItem(anyLong(),anyLong());
    }
    @Test void publicStockEndpointAccessible() throws Exception {
        when(carts.getVariantStock(10L)).thenReturn(StockCheckResponse.builder().variantId(10L).availableStock(2).purchasable(true).build());
        mvc.perform(get("/api/products/variants/10/stock")).andExpect(status().isOk()).andExpect(jsonPath("$.availableStock").value(2));
    }
    @Test void cartPageRendersEmptyStateWithoutBrokenCheckoutLink() throws Exception {
        var result=mvc.perform(get("/cart").with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Giỏ hàng của bạn đang trống"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/cart-empty-preview.html"),result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void publicHomepageDoesNotReadUserCart() throws Exception {
        when(catalog.listProducts(1, "")).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        mvc.perform(get("/home")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa có sản phẩm")));
        verifyNoInteractions(carts,customer);
    }
    @Test void searchPassesKeywordToDatabaseServiceAndRendersQuery() throws Exception {
        when(catalog.listProducts(1, "Xoài")).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        mvc.perform(get("/home").param("keyword","Xoài")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kết quả cho “Xoài”")));
        verify(catalog).listProducts(1,"Xoài");
    }
    @Test void missingProductReturnsHtml404() throws Exception {
        when(catalog.getProduct(999L,null,null)).thenThrow(new ResourceNotFoundException("Sản phẩm không còn được bán."));
        mvc.perform(get("/product-detail/999")).andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Khám phá sản phẩm khác")));
    }
    @Test void populatedCartRendersWarningsFormsAndCsrf() throws Exception {
        CartItemDto item=CartItemDto.builder().cartItemId(1L).variantId(10L).productName("Bưởi da xanh").variantName("1kg")
                .shopId(2L).shopName("Cửa hàng A").quantity(2).availableStock(5).quantityEditable(true).purchasable(true)
                .priceChanged(true).previousPrice(new BigDecimal("140000")).unitPriceSnapshot(new BigDecimal("150000"))
                .itemSubtotal(new BigDecimal("300000")).imageUrl("/images/buoi-da-xanh.jpg").build();
        CartItemDto shortage=CartItemDto.builder().cartItemId(2L).variantId(20L).productName("Sầu riêng").variantName("Hộp 1kg")
                .shopId(3L).shopName("Cửa hàng B").quantity(5).availableStock(2).quantityEditable(true).purchasable(false)
                .previousPrice(new BigDecimal("220000")).unitPriceSnapshot(new BigDecimal("220000"))
                .itemSubtotal(new BigDecimal("1100000")).imageUrl("/images/sau-rieng.jpg")
                .availabilityMessage("Chỉ còn 2 sản phẩm. Vui lòng giảm số lượng.").build();
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().items(List.of(item,shortage)).totalItems(7)
                .hasUnavailableItems(true).totalAmount(new BigDecimal("300000")).build());
        var result=mvc.perform(get("/cart").with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Giá đã thay đổi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andReturn();
        java.nio.file.Path preview=java.nio.file.Path.of("target/cart-preview.html");
        java.nio.file.Files.writeString(preview,result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void productDetailRendersGuestAndCustomerActions() throws Exception {
        var variant=CatalogProductResponse.VariantResponse.builder().variantId(10L).variantName("1kg").price(new BigDecimal("150000"))
                .status("ACTIVE").availableStock(5).purchasable(true).build();
        var product=CatalogProductResponse.builder().productId(1L).name("Bưởi").shopName("Shop A").imageUrl("/images/buoi-da-xanh.jpg")
                .description("Trái cây tươi").variants(List.of(variant)).selectedVariant(variant).build();
        when(catalog.getProduct(10L,null,null)).thenReturn(product);
        var guest=mvc.perform(get("/product-detail/10")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đăng nhập để thêm vào giỏ"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/product-guest-preview.html"),guest.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        var buyer=mvc.perform(get("/product-detail/10").with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thêm vào giỏ hàng"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/product-customer-preview.html"),buyer.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        when(catalog.listProducts(1, "")).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(product)));
        var home=mvc.perform(get("/home")).andExpect(status().isOk()).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/home-preview.html"),home.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
