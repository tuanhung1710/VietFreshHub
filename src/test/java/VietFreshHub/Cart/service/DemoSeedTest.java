package VietFreshHub.Cart.service;

import VietFreshHub.Auth.repository.*;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Cart.service.impl.CartServiceImpl;
import VietFreshHub.Product.repository.ProductRepository;
import VietFreshHub.Shop.repository.ShopRepository;
import VietFreshHub.config.DataInitializer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true", "spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:demoseed;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "app.seed.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Import({DataInitializer.class, AuthService.class, CartServiceImpl.class,
        VietFreshHub.Product.service.impl.CatalogServiceImpl.class, DemoSeedTest.Beans.class})
class DemoSeedTest {
    private static final String PASSWORD = java.util.UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.seed.password", () -> PASSWORD); }
    @TestConfiguration static class Beans { @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); } }
    @Autowired DataInitializer initializer;
    @Autowired UserRepository users;
    @Autowired ShopRepository shops;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository items;
    @Autowired CartServiceImpl cartService;
    @Autowired AuthService auth;
    @Autowired EntityManager em;

    @Test void seedHasValidRelatedRecordsAndPasswordLogin() {
        assertEquals(9,users.count()); assertEquals(4,shops.count()); assertEquals(18,products.count());
        assertEquals(4L,em.createQuery("select count(c) from Category c",Long.class).getSingleResult());
        assertEquals(7,items.count());
        assertNotNull(auth.login(DataInitializer.CUSTOMER_EMAIL,PASSWORD));
        assertNull(auth.login("blocked.demo@vietfreshhub.example",PASSWORD));
        assertTrue(auth.login("manager-a.demo@vietfreshhub.example",PASSWORD).getAuthorities().stream()
                .anyMatch(a -> "ROLE_STORE_MANAGER".equals(a.getAuthority())));
    }
    @Test void cartExercisesPriceChangeShortageExpiredAndClosedShop() {
        var user=users.findByEmailIgnoreCase(DataInitializer.CUSTOMER_EMAIL).orElseThrow();
        var cart=cartService.getActiveCart(user.getUserId());
        assertEquals(7,cart.getItems().size()); assertTrue(cart.isHasUnavailableItems());
        assertEquals(1,cart.getItems().stream().filter(i -> i.isPriceChanged()).count());
        assertEquals(5,cart.getItems().stream().filter(i -> !i.isPurchasable()).count());
        assertEquals(new BigDecimal("223000.00"),cart.getTotalAmount());
        assertTrue(cartService.getActiveCart(users.findByEmailIgnoreCase("empty.demo@vietfreshhub.example").orElseThrow().getUserId()).getItems().isEmpty());
    }
    @Test void secondRunDoesNotResetTesterCart() {
        var user=users.findByEmailIgnoreCase(DataInitializer.CUSTOMER_EMAIL).orElseThrow();
        var item=carts.findByUserUserIdAndStatus(user.getUserId(),"ACTIVE").orElseThrow().getItems().getFirst();
        item.setQuantity(3); em.flush(); initializer.run(); em.clear();
        assertEquals(3,items.findById(item.getCartItemId()).orElseThrow().getQuantity());
        assertEquals(9,users.count()); assertEquals(18,products.count());
    }
    @Test void demoCatalogSearchAndPaginationUseApprovedShopData() {
        var page=products.findVisibleCatalog("Xoài",org.springframework.data.domain.PageRequest.of(0,12));
        assertEquals(4,page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(p -> p.getName().contains("Xoài")));
        assertEquals(17,products.findVisibleCatalog(org.springframework.data.domain.PageRequest.of(0,12)).getTotalElements());
    }
}
