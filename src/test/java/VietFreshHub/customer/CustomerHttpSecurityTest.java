package VietFreshHub.customer;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.*;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.service.*;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.order.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class CustomerHttpSecurityTest {
    final UserRepository users = mock(UserRepository.class);
    final UserRoleRepository roles = mock(UserRoleRepository.class);
    final CartService cart = mock(CartService.class);
    final CheckoutService checkout = mock(CheckoutService.class);
    final CustomerOrderService orders = mock(CustomerOrderService.class);
    final CustomerOrderQueryService details = mock(CustomerOrderQueryService.class);
    CustomerHttpHarness http;
    @BeforeEach void start() {
        User buyer = new User(); buyer.setUserId(1L); buyer.setStatus("ACTIVE"); buyer.setEmail("buyer@test.invalid");
        when(users.findByEmailIgnoreCase(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_CUSTOMER"));
        http = new CustomerHttpHarness(users, roles, cart, mock(CatalogService.class), checkout,
                mock(CheckoutShippingQuoteService.class), new CheckoutDraftStore(),
                mock(CustomerAddressService.class), orders, details);
    }
    @AfterEach void close() { http.close(); }

    @ParameterizedTest @ValueSource(strings={"/cart","/customer/checkout","/customer/orders","/customer/orders/10","/customer/orders/10/confirmation","/customer/orders/10/tracking","/customer/payments"})
    void anonymousGetsLoginBeforeAnyBusinessCall(String path) throws Exception {
        http.mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(cart, checkout, orders, details);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","STORE_MANAGER","DELIVERY_STAFF"})
    void wrongRoleCannotEnterCustomerPages(String role) throws Exception {
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_" + role));
        for (String path:List.of("/customer/orders","/customer/orders/10","/customer/orders/10/confirmation",
                "/customer/orders/10/tracking","/customer/payments")) {
            http.mvc.perform(get(path).with(user("buyer@test.invalid").roles(role))).andExpect(status().isForbidden());
        }
        for (String path : List.of("/cart/clear", "/customer/checkout", "/customer/orders/10/cancel", "/customer/orders/10/reorder")) {
            http.mvc.perform(post(path).with(user("buyer@test.invalid").roles(role)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(cart, checkout, orders, details);
    }
    @ParameterizedTest @ValueSource(strings={"/cart/add","/cart/update","/cart/remove","/cart/clear","/customer/checkout","/customer/orders/10/cancel","/customer/orders/10/reorder"})
    void missingCsrfBlocksEveryMutation(String path) throws Exception {
        http.mvc.perform(post(path).with(user("buyer@test.invalid").roles("CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(cart, checkout, orders, details);
    }
    @ParameterizedTest @ValueSource(strings={"/cart/clear","/customer/orders/10/cancel","/customer/orders/10/reorder"})
    void invalidCsrfIsRejected(String path) throws Exception {
        http.mvc.perform(post(path).with(user("buyer@test.invalid").roles("CUSTOMER")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden()); verifyNoInteractions(cart,orders);
    }
    @Test void validCsrfUsesServerResolvedCustomerInsteadOfPostedId() throws Exception {
        http.mvc.perform(post("/cart/clear").param("customerId","999").with(user("buyer@test.invalid").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        verify(cart).clearCart(1L); verifyNoMoreInteractions(cart);
    }
    @Test void lockedAccountExpiresEvenWithValidSessionRoleAndCsrf() throws Exception {
        users.findByEmailIgnoreCase("buyer@test.invalid").orElseThrow().setStatus("BLOCKED");
        http.mvc.perform(post("/cart/clear").with(user("buyer@test.invalid").roles("CUSTOMER")).with(csrf()))
                .andExpect(redirectedUrl("/login?accountStatusChanged=true")); verifyNoInteractions(cart);
    }
    @Test void roleChangedInDatabaseExpiresStaleSession() throws Exception {
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_STORE_MANAGER"));
        http.mvc.perform(get("/customer/orders").with(user("buyer@test.invalid").roles("CUSTOMER")))
                .andExpect(redirectedUrl("/login?accountRolesChanged=true")); verifyNoInteractions(orders);
    }
    @ParameterizedTest @ValueSource(strings={"/customer/orders","/customer/orders/10","/customer/orders/10/tracking","/customer/payments"})
    void inactiveAccountCannotReadCustomerPages(String path) throws Exception {
        users.findByEmailIgnoreCase("buyer@test.invalid").orElseThrow().setStatus("BLOCKED");
        http.mvc.perform(get(path).with(user("buyer@test.invalid").roles("CUSTOMER")))
                .andExpect(redirectedUrl("/login?accountStatusChanged=true"));
        verifyNoInteractions(orders,details,cart,checkout);
    }
    @ParameterizedTest @ValueSource(strings={"/customer/orders/10/cancel","/customer/orders/10/reorder"})
    void navigationCannotInvokePostActionsViaGet(String path) throws Exception {
        http.mvc.perform(get(path).with(user("buyer@test.invalid").roles("CUSTOMER")))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(orders);
    }
}
