package VietFreshHub.loyalty;

import VietFreshHub.auth.entity.*;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.*;
import VietFreshHub.cart.entity.*;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.repository.CheckoutAddressRepository;
import VietFreshHub.checkout.service.*;
import VietFreshHub.coupon.entity.*;
import VietFreshHub.coupon.repository.*;
import VietFreshHub.coupon.services.CouponApplicationServiceImpl;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.loyalty.entity.*;
import VietFreshHub.loyalty.repository.*;
import VietFreshHub.loyalty.services.LoyaltyServiceImpl;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.promotion.entity.*;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA, repositories, coupon and Loyalty writes against an isolated in-memory DB. */
class LoyaltyCheckoutTransactionTest {
    private EntityManagerFactory factory;
    private EntityManager em;
    private TransactionTemplate tx;
    private JpaRepositoryFactory repositories;
    private LoyaltyServiceImpl loyalty;
    private CheckoutServiceImpl checkout;
    private ApplicationEventPublisher events;
    private Long customerId;
    private CheckoutDraft draft;
    private CheckoutRequest request;

    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void amount(String expected, BigDecimal actual) { assertEquals(0, money(expected).compareTo(actual)); }

    @BeforeEach
    void setup() {
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver",
                "hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MSSQLServer;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.hbm2ddl.create_namespaces", "false",
                "hibernate.hbm2ddl.halt_on_error", "true")).build();
        var metadata = new MetadataSources(registry);
        Set<Class<?>> entities = new LinkedHashSet<>();
        for (var type : List.of(LoyaltyAccount.class, LoyaltyTransaction.class, LoyaltyRule.class,
                Order.class, ShopOrder.class, OrderAddress.class, OrderStatusHistory.class,
                Payment.class, Cart.class, Coupon.class, CouponUsage.class)) collect(type, entities);
        entities.forEach(metadata::addAnnotatedClass);
        factory = metadata.buildMetadata().buildSessionFactory();
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        tx = new TransactionTemplate(new JpaTransactionManager(factory));
        repositories = new JpaRepositoryFactory(em);
        loyalty = new LoyaltyServiceImpl(repositories.getRepository(LoyaltyAccountRepository.class),
                repositories.getRepository(LoyaltyTransactionRepository.class), repositories.getRepository(LoyaltyRuleRepository.class), em);
        tx.executeWithoutResult(status -> seed());
    }

    private static void collect(Class<?> type, Set<Class<?>> entities) {
        if (!type.isAnnotationPresent(Entity.class) || !entities.add(type)) return;
        for (var field : type.getDeclaredFields()) {
            collect(field.getType(), entities);
            if (field.getGenericType() instanceof ParameterizedType generic)
                for (var arg : generic.getActualTypeArguments()) if (arg instanceof Class<?> child) collect(child, entities);
        }
    }

    private void seed() {
        // Native coupon scope/discount tables have no JPA entities in the application.
        for (String ddl : List.of(
                "CREATE TABLE IF NOT EXISTS dbo.promotion_products(promotion_id BIGINT, product_id BIGINT)",
                "CREATE TABLE IF NOT EXISTS dbo.promotion_variants(promotion_id BIGINT, variant_id BIGINT)",
                "CREATE TABLE IF NOT EXISTS dbo.promotion_categories(promotion_id BIGINT, category_id BIGINT)",
                "CREATE TABLE IF NOT EXISTS dbo.product_categories(product_id BIGINT, category_id BIGINT)",
                "CREATE TABLE IF NOT EXISTS dbo.order_discounts(order_id BIGINT, shop_order_id BIGINT, order_item_id BIGINT, promotion_id BIGINT, coupon_id BIGINT, discount_amount DECIMAL(18,2), created_at TIMESTAMP)"))
            em.createNativeQuery(ddl).executeUpdate();
        var user = new User(); user.setEmail("loyalty@test.invalid"); user.setFullName("Loyalty Test"); em.persist(user); customerId = user.getUserId();
        var rule = new LoyaltyRule(); rule.setRuleId(1L); rule.setEarningPercent(money("1.00")); rule.setEarningEnabled(true);
        rule.setMinRedemptionAmount(money("1000")); rule.setMaxRedemptionPercent(20); rule.setRedemptionEnabled(true); em.persist(rule);
        em.persist(LoyaltyAccount.builder().user(user).rewardBalance(money("50000")).totalEarnedAmount(money("50000")).build());
        var address = new Address(); address.setUser(user); address.setRecipientName("Buyer"); address.setPhone("0900000000");
        address.setProvince("HCM"); address.setWard("Ward"); address.setAddressLine("1 Test"); em.persist(address);
        var method = new PaymentMethod(); method.setMethodCode("COD"); method.setName("COD"); em.persist(method);
        var shop = new Shop(); shop.setShopName("Fresh"); em.persist(shop);
        var product = new CatalogProduct(); product.setShop(shop); product.setName("Fruit"); product.setSlug("fruit"); em.persist(product);
        var variant = new CatalogVariant(); variant.setProduct(product); variant.setSku("TEST"); variant.setVariantName("Box");
        variant.setThumbnailUrl("/test.png"); variant.setUnit("box"); variant.setPrice(money("200000")); em.persist(variant);
        var cart = new Cart(); cart.setUser(user); em.persist(cart);
        var item = new CartItem(); item.setCart(cart); item.setVariant(variant); item.setQuantity(1); item.setUnitPriceSnapshot(money("200000"));
        em.persist(item); cart.getItems().add(item);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var promotion = Promotion.builder().shopId(shop.getShopId()).name("Coupon").discountType(DiscountType.FIXED)
                .discountValue(money("20000")).startAt(now.minusDays(1)).endAt(now.plusDays(1)).status(PromotionStatus.ACTIVE).build();
        em.persist(promotion);
        em.persist(Coupon.builder().promotion(promotion).code("SAVE20").startAt(now.minusDays(1)).endAt(now.plusDays(1)).status(CouponStatus.ACTIVE).build());
        em.flush();
        var dto = CartDto.builder().cartId(cart.getCartId()).userId(customerId).status("ACTIVE").totalItems(1).totalAmount(money("200000"))
                .items(List.of(CartItemDto.builder().cartItemId(item.getCartItemId()).shopId(shop.getShopId()).variantId(variant.getVariantId())
                        .quantity(1).unitPriceSnapshot(money("200000")).itemSubtotal(money("200000")).purchasable(true).build())).build();
        var carts = mock(CartService.class); when(carts.getActiveCart(customerId)).thenReturn(dto);
        var addresses = mock(CustomerAddressService.class);
        when(addresses.getDeliveryAddress(customerId, address.getAddressId())).thenAnswer(call -> em.find(Address.class, address.getAddressId()));
        when(addresses.isDeliveryLocationComplete(any())).thenReturn(true);
        var catalog = mock(CatalogService.class); when(catalog.isPurchasable(any())).thenReturn(true);
        var shipping = mock(CheckoutShippingQuoteService.class);
        when(shipping.validateForConfirmation(any(), any(), any())).thenReturn(money("15000"));
        events = mock(ApplicationEventPublisher.class);
        var coupons = new CouponApplicationServiceImpl(repositories.getRepository(CouponRepository.class), repositories.getRepository(CouponUsageRepository.class), em);
        // Catalog fixtures stay immutable. Avoid the Hibernate refresh cycle on the
        // bidirectional catalog graph in H2, while retaining real database locks.
        // Coupon, order, payment and Loyalty still use the real EntityManager.
        EntityManager checkoutEm = mock(EntityManager.class, org.mockito.AdditionalAnswers.delegatesTo(em));
        for (Class<?> type : List.of(Shop.class, CatalogProduct.class, CatalogVariant.class))
            doAnswer(call -> { em.lock(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(checkoutEm).refresh(isA(type), any(LockModeType.class));
        checkout = new CheckoutServiceImpl(repositories.getRepository(UserRepository.class), repositories.getRepository(CartRepository.class), carts,
                repositories.getRepository(CheckoutAddressRepository.class), addresses, repositories.getRepository(PaymentMethodRepository.class),
                repositories.getRepository(OrderRepository.class), catalog, mock(InventoryReservationService.class), shipping, checkoutEm, events, coupons, loyalty);
        var preview = checkout.preview(customerId, List.of(item.getCartItemId()));
        String token = "0123456789abcdef0123456789abcdef";
        var quote = new ShopShippingQuote(shop.getShopId(), null, null, null, null, money("1000"), money("15000"));
        draft = new CheckoutDraft(token, customerId, cart.getCartId(), preview.fingerprint(), address.getAddressId(), Map.of(shop.getShopId(), quote), money("15000"), List.of(item.getCartItemId()));
        request = new CheckoutRequest(); request.setCheckoutToken(token); request.setAddressId(address.getAddressId());
        request.setCouponCode("SAVE20"); request.setRewardAmountToUse(money("10000"));
    }

    @AfterEach
    void close() { if (factory != null) factory.close(); }

    @Test
    void checkoutCreatesFinalPaymentAndRetryDoesNotRedeemAgain() {
        var result = tx.execute(status -> checkout.confirm(customerId, draft, request));
        tx.executeWithoutResult(status -> {
            var order = em.find(Order.class, result.orderId()); amount("185000", order.getGrandTotal());
            amount("185000", em.createQuery("select p.amount from Payment p", BigDecimal.class).getSingleResult());
            amount("40000", loyalty.getBalance(customerId).rewardBalance());
            assertEquals(Order.Status.PENDING, order.getStatus()); assertEquals("UNPAID", order.getPaymentStatus());
            assertEquals(result.orderId(), checkout.confirm(customerId, draft, request).orderId());
            amount("40000", loyalty.getBalance(customerId).rewardBalance());
            assertEquals(1L, em.createQuery("select count(t) from LoyaltyTransaction t", Long.class).getSingleResult());
            order.setStatus(Order.Status.COMPLETED); order.setPaymentStatus("PAID"); em.flush();
            amount("1700", loyalty.earnReward(order.getOrderId()));
            amount("0", loyalty.earnReward(order.getOrderId()));
        });
    }

    @Test
    void failureAfterPaymentFlushRollsBackRewardCouponOrderAndCart() {
        doThrow(new IllegalStateException("simulated order failure")).when(events).publishEvent(any(Object.class));
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> checkout.confirm(customerId, draft, request)));
        tx.executeWithoutResult(status -> {
            amount("50000", loyalty.getBalance(customerId).rewardBalance());
            for (String entity : List.of("Order", "ShopOrder", "Payment", "LoyaltyTransaction", "CouponUsage"))
                assertEquals(0L, em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(), entity);
            assertEquals(0, ((Number) em.createNativeQuery("select count(*) from dbo.order_discounts").getSingleResult()).intValue());
            assertEquals("ACTIVE", em.find(Cart.class, draft.cartId()).getStatus());
        });
    }
}
