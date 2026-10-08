package VietFreshHub.Cart.service;

import VietFreshHub.Auth.entity.*;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.entity.*;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Cart.service.impl.CartServiceImpl;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.repository.ProductRepository;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.Shop.entity.Shop;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true", "spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:carttest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@Import({CartServiceImpl.class, VietFreshHub.Product.service.impl.CatalogServiceImpl.class})
class CartPersistenceTest {
    @Autowired TestEntityManager em;
    @Autowired InventoryBatchRepository inventory;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository items;
    @Autowired CartServiceImpl service;
    @Autowired CatalogService catalog;
    @Autowired UserRepository users;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    User user; ProductVariant variant;

    @BeforeEach void setup() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> createFixture());
    }
    private void createFixture() {
        user = new User(); user.setEmail("buyer@example.com"); user.setFullName("Khách hàng"); user.setStatus("ACTIVE"); em.persist(user);
        SellerApplication application = new SellerApplication(); application.setUser(user); application.setBusinessName("Shop");
        application.setStatus(SellerApplicationStatus.APPROVED); em.persist(application);
        Shop shop = new Shop(); shop.setShopName("Shop"); shop.setApplicationId(application.getApplicationId()); em.persist(shop);
        Product product = new Product(); product.setName("Bưởi"); product.setSlug("buoi"); product.setShop(shop);
        product.setApprovalStatus("APPROVED"); em.persist(product);
        variant = new ProductVariant(); variant.setProduct(product); variant.setSku("B-1"); variant.setVariantName("1kg");
        variant.setPrice(new BigDecimal("150000")); em.persist(variant); em.flush();
    }
    private void batch(String code, LocalDate expiry, int onHand, int reserved) {
        InventoryBatch b = new InventoryBatch(); b.setVariant(variant); b.setBatchCode(code); b.setExpiryDate(expiry);
        b.setQuantityOnHand(onHand); b.setReservedQuantity(reserved); em.persist(b); em.flush();
    }
    @Test void stockExcludesExpiredAndUndatedBatchesAndSubtractsReservations() {
        LocalDate day=LocalDate.of(2026,10,8);
        batch("expired",day.minusDays(1),100,0); batch("unknown",null,100,0);
        batch("today",day,5,2); batch("future",day.plusDays(1),7,1);
        assertEquals(9L,inventory.findAvailableStock(variant.getVariantId(),day));
        var bulk=inventory.findAvailableStockForVariants(java.util.List.of(variant.getVariantId()),day);
        assertEquals(1,bulk.size()); assertEquals(9L,((Number)bulk.getFirst()[1]).longValue());
    }
    @Test void visibleCatalogQueryRequiresSellerApproval() {
        assertEquals(1,products.findVisibleCatalog(org.springframework.data.domain.PageRequest.of(0,12)).getTotalElements());
        variant.getProduct().setApprovalStatus("PENDING"); em.flush();
        assertTrue(products.findVisibleCatalog(org.springframework.data.domain.PageRequest.of(0,12)).isEmpty());
    }
    @Test void closedShopRemainsBrowsableButCannotBePurchased() {
        variant.getProduct().getShop().setAvailabilityStatus("CLOSED"); em.flush();
        assertEquals(1,products.findVisibleCatalog(org.springframework.data.domain.PageRequest.of(0,12)).getTotalElements());
        assertFalse(catalog.isPurchasable(variant));
    }
    @Test void stockSumUsesWideIntegerAndUiStockDoesNotOverflow() {
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        batch("large-a",today,Integer.MAX_VALUE,0); batch("large-b",today,Integer.MAX_VALUE,0);
        assertEquals(4294967294L,inventory.findAvailableStock(variant.getVariantId(),today));
        assertEquals(Integer.MAX_VALUE,inventory.getAvailableStockByVariantId(variant.getVariantId()));
    }
    @Test void catalogPaginationReturnsOnlyRequestedPage() {
        Product second=new Product(); second.setName("Xoài"); second.setSlug("xoai"); second.setShop(variant.getProduct().getShop());
        second.setApprovalStatus("APPROVED"); em.persist(second); em.flush();
        var page=products.findVisibleCatalog(org.springframework.data.domain.PageRequest.of(0,1));
        assertEquals(1,page.getContent().size()); assertEquals(2,page.getTotalElements()); assertTrue(page.hasNext());
    }
    @Test void userLockAndOrphanRemovalPersistClear() {
        Cart cart=new Cart(); cart.setUser(user); em.persist(cart);
        CartItem item=new CartItem(); item.setCart(cart); item.setVariant(variant); item.setQuantity(2);
        item.setUnitPriceSnapshot(variant.getPrice()); cart.getItems().add(item); em.persist(item); em.flush();
        Long cartId=cart.getCartId(); service.clearCart(user.getUserId()); em.flush(); em.clear();
        assertEquals(0,items.count()); assertEquals("ACTIVE",carts.findById(cartId).orElseThrow().getStatus());
    }
    @Test void removingOneItemPersistsWithoutDeletingCart() {
        Cart cart=new Cart(); cart.setUser(user); em.persist(cart);
        CartItem item=new CartItem(); item.setCart(cart); item.setVariant(variant); item.setQuantity(2);
        item.setUnitPriceSnapshot(variant.getPrice()); cart.getItems().add(item); em.persist(item); em.flush();
        service.removeCartItem(user.getUserId(),variant.getVariantId()); em.flush(); em.clear();
        assertEquals(0,items.count()); assertEquals(1,carts.count());
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentAddsCreateOneCartAndAccumulateQuantity() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> batch("fresh", LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1),10,0));
        Long userId=user.getUserId(), variantId=variant.getVariantId();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Void> add=() -> {
                start.await(); service.addToCart(userId,new VietFreshHub.Cart.dto.AddToCartRequest(variantId,1)); return null;
            };
            var a=executor.submit(add); var b=executor.submit(add); start.countDown();
            a.get(10,java.util.concurrent.TimeUnit.SECONDS); b.get(10,java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                var cart=carts.findByUserUserIdAndStatus(userId,"ACTIVE").orElseThrow();
                assertEquals(1,cart.getItems().size()); assertEquals(2,cart.getItems().getFirst().getQuantity());
                assertEquals(1,carts.count());
            });
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> {
                em.getEntityManager().createQuery("delete from CartItem").executeUpdate();
                em.getEntityManager().createQuery("delete from Cart").executeUpdate();
                em.getEntityManager().createQuery("delete from InventoryBatch").executeUpdate();
                em.getEntityManager().createQuery("delete from ProductVariant").executeUpdate();
                em.getEntityManager().createQuery("delete from Product").executeUpdate();
                em.getEntityManager().createQuery("delete from Shop").executeUpdate();
                em.getEntityManager().createQuery("delete from SellerApplication").executeUpdate();
                em.getEntityManager().createQuery("delete from User").executeUpdate();
            });
        }
    }
}
