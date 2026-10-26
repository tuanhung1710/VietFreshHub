package VietFreshHub.catalog;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.AddToCartResponse;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.checkout.service.CheckoutShippingQuoteService;
import VietFreshHub.customer.CustomerHttpHarness;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.CustomerOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production security chain, binding, JSON and current-account checks. No inventory mutation is mocked as proof of concurrency. */
class ProductStockHttpTest {
    final UserRepository users = mock(UserRepository.class);
    final UserRoleRepository roles = mock(UserRoleRepository.class);
    final CartService carts = mock(CartService.class);
    final CatalogService catalog = mock(CatalogService.class);
    CustomerHttpHarness http;

    @BeforeEach void start() {
        User buyer = new User();
        buyer.setUserId(7L); buyer.setEmail("stock@test.invalid"); buyer.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(roles.findRoleNamesByUserId(7L)).thenReturn(List.of("ROLE_CUSTOMER"));
        http = new CustomerHttpHarness(users, roles, carts, catalog, mock(CheckoutService.class),
                mock(CheckoutShippingQuoteService.class), new CheckoutDraftStore(), mock(CustomerAddressService.class),
                mock(CustomerOrderService.class), mock(CustomerOrderQueryService.class));
    }

    @AfterEach void close() { http.close(); }

    @Test void readsAllPublicVariantsWithNoStoreAndNoInternalStockData() throws Exception {
        var option = CatalogProductResponse.VariantResponse.builder().variantId(11L).variantName("Hộp 1 kg")
                .sku("internal-sku").price(BigDecimal.TEN).imageUrl("/images/fruit.jpg")
                .availableStock(0).purchasable(false).availabilityMessage("Tạm hết hàng.").build();
        when(catalog.getProduct(null, 1L, null)).thenReturn(CatalogProductResponse.builder()
                .productId(1L).variants(List.of(option)).selectedVariant(option).build());
        http.mvc.perform(get("/api/catalog/products/1/availability").with(user("stock@test.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.productId").value(1)).andExpect(jsonPath("$.variants[0].availableStock").value(0))
                .andExpect(jsonPath("$.variants[0].purchasable").value(false))
                .andExpect(jsonPath("$.variants[0].sku").doesNotExist())
                .andExpect(jsonPath("$.variants[0].reservedQuantity").doesNotExist());
    }

    @Test void hiddenProductReturnsStructured404() throws Exception {
        when(catalog.getProduct(null, 1L, null)).thenThrow(new ResourceNotFoundException("Sản phẩm không khả dụng."));
        http.mvc.perform(get("/api/catalog/products/1/availability").with(user("stock@test.invalid").roles("CUSTOMER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test void nativeProductLinkThatJustBecameUnavailableRedirectsWithBusinessMessage() throws Exception {
        when(catalog.getProduct(11L, null, null)).thenThrow(new ResourceNotFoundException("Sản phẩm không còn được bán."));
        http.mvc.perform(get("/product-detail/11").with(user("stock@test.invalid").roles("CUSTOMER")))
                .andExpect(redirectedUrl("/home"))
                .andExpect(flash().attribute("errorMessage", "Sản phẩm không còn được bán."));
    }

    @Test void stockConflictKeepsRequestedQuantityAndUsesResolvedIdentity() throws Exception {
        when(carts.addToCart(eq(7L), any(AddToCartRequest.class)))
                .thenThrow(new OutOfStockException("Hộp 1 kg: chỉ còn 1 sản phẩm.", 1));
        http.mvc.perform(post("/api/cart/items").with(user("stock@test.invalid").roles("CUSTOMER")).with(csrf())
                        .param("variantId", "11").param("quantity", "3").param("customerId", "999"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.variantId").value(11)).andExpect(jsonPath("$.requestedQuantity").value(3))
                .andExpect(jsonPath("$.availableStock").value(1));
        verify(carts, never()).getActiveCart(any());
    }

    @Test void successfulAddReturnsActualCartBadgeCount() throws Exception {
        when(carts.addToCart(eq(7L), any())).thenReturn(AddToCartResponse.builder()
                .cartItemId(80L).quantity(3).message("Đã thêm sản phẩm vào giỏ hàng.").build());
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().totalItems(8).build());
        http.mvc.perform(post("/api/cart/items").with(user("stock@test.invalid").roles("CUSTOMER")).with(csrf())
                        .param("variantId", "11").param("quantity", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(8));
    }

    @Test void invalidQuantityDoesNotCallCartService() throws Exception {
        http.mvc.perform(post("/api/cart/items").with(user("stock@test.invalid").roles("CUSTOMER")).with(csrf())
                        .param("variantId", "11").param("quantity", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        verifyNoInteractions(carts);
    }

    @Test void apiRequiresCustomerAndCsrf() throws Exception {
        http.mvc.perform(get("/api/catalog/products/1/availability")).andExpect(redirectedUrl("/login"));
        http.mvc.perform(post("/api/cart/items").with(user("stock@test.invalid").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        when(roles.findRoleNamesByUserId(7L)).thenReturn(List.of("ROLE_ADMIN"));
        http.mvc.perform(get("/api/catalog/products/1/availability").with(user("stock@test.invalid").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(carts, catalog);
    }
}
