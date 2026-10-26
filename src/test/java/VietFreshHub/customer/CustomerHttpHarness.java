package VietFreshHub.customer;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.auth.service.Oidc_UserService;
import VietFreshHub.cart.controller.CustomerCartViewController;
import VietFreshHub.cart.controller.CartApiController;
import VietFreshHub.catalog.controller.ProductAvailabilityController;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.controller.CheckoutController;
import VietFreshHub.checkout.service.*;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.order.controller.CustomerOrderController;
import VietFreshHub.order.controller.CustomerOrderDetailsController;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.payment.controller.CustomerPaymentHistoryController;
import VietFreshHub.payment.service.CustomerPaymentHistoryService;
import VietFreshHub.security.SecurityConfig;
import VietFreshHub.vietmap.service.VietMapService;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Production security and controllers; deliberately no application scan, scheduler or provider calls. */
public final class CustomerHttpHarness implements AutoCloseable {
    private final AnnotationConfigWebApplicationContext context;
    public final MockMvc mvc;

    public CustomerHttpHarness(UserRepository users, UserRoleRepository roles, CartService cart,
            CatalogService catalog, CheckoutService checkout, CheckoutShippingQuoteService shipping,
            CheckoutDraftStore drafts, CustomerAddressService addresses, CustomerOrderService orders,
            CustomerOrderQueryService details) {
        this(users, roles, cart, catalog, checkout, shipping, drafts, addresses, orders, details,
                mock(CustomerPaymentHistoryService.class));
    }
    public CustomerHttpHarness(UserRepository users, UserRoleRepository roles, CartService cart,
            CatalogService catalog, CheckoutService checkout, CheckoutShippingQuoteService shipping,
            CheckoutDraftStore drafts, CustomerAddressService addresses, CustomerOrderService orders,
            CustomerOrderQueryService details, CustomerPaymentHistoryService payments) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfiguration.class, CustomerCartViewController.class, CheckoutController.class,
                CustomerOrderController.class, CustomerOrderDetailsController.class, CustomerPaymentHistoryController.class,
                CartApiController.class, ProductAvailabilityController.class);
        context.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("userRepository", users);
            beans.registerSingleton("userRoleRepository", roles);
            beans.registerSingleton("currentCustomerService", new CurrentCustomerService(users));
            beans.registerSingleton("oidcUserService", mock(Oidc_UserService.class));
            beans.registerSingleton("cartService", cart);
            beans.registerSingleton("catalogService", catalog);
            beans.registerSingleton("productRecommendations", mock(VietFreshHub.recommendation.service.ProductRecommendationService.class));
            beans.registerSingleton("checkoutService", checkout);
            beans.registerSingleton("shippingQuotes", shipping);
            beans.registerSingleton("drafts", drafts);
            beans.registerSingleton("checkoutAddresses", mock(CheckoutAddressService.class));
            beans.registerSingleton("customerAddresses", addresses);
            beans.registerSingleton("vietMapService", mock(VietMapService.class));
            beans.registerSingleton("customerOrders", orders);
            beans.registerSingleton("customerOrderDetails", details);
            beans.registerSingleton("customerPaymentHistory", payments);
        });
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class WebConfiguration {
        @Bean ClientRegistrationRepository clientRegistrationRepository() {
            // Satisfies the production OAuth configuration; no login/provider request is executed.
            var client = ClientRegistration.withRegistrationId("test")
                    .clientId("test").clientSecret("unused")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid")
                    .authorizationUri("https://example.invalid/authorize")
                    .tokenUri("https://example.invalid/token").jwkSetUri("https://example.invalid/jwks")
                    .userInfoUri("https://example.invalid/userinfo").userNameAttributeName("sub").build();
            return new InMemoryClientRegistrationRepository(client);
        }
        @Bean ViewResolver views() {
            // HTTP security/binding/model tests; template rendering is covered separately.
            return (name, locale) -> {
                if (name.startsWith("redirect:")) {
                    return new org.springframework.web.servlet.view.RedirectView(name.substring(9), true);
                }
                return (model, request, response) -> {};
            };
        }
    }
    @Override public void close() { context.close(); }
}
