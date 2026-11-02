package VietFreshHub.product.service.impl;

import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-quota-concurrency;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductQuotaConcurrencyTest {
    private static final AtomicLong IDS = new AtomicLong();

    @Autowired ShopRepository shops;
    @Autowired ProductRepository products;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shopLockSerializesQuotaCheckAndProductInsert() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Long shopId = tx.execute(status -> {
            Shop shop = new Shop();
            shop.setShopName("Quota concurrency " + IDS.incrementAndGet());
            shops.saveAndFlush(shop);
            for (int i = 0; i < 4; i++) products.saveAndFlush(product(shop.getShopId(), ProductStatus.ACTIVE));
            products.saveAndFlush(product(shop.getShopId(), ProductStatus.DELETED));
            return shop.getShopId();
        });
        assertNotNull(shopId);
        assertEquals(4L, products.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED));

        CountDownLatch firstHasShopLock = new CountDownLatch(1);
        CountDownLatch letFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> first = executor.submit(() -> tx.execute(status -> {
            assertNotNull(shops.findByShopIdForUpdate(shopId).orElse(null));
            ProductServiceImpl.ensureProductCapacity(
                    products.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED), 5);
            firstHasShopLock.countDown();
            assertTrue(await(letFirstCommit));
            products.saveAndFlush(product(shopId, ProductStatus.ACTIVE));
            return true;
        }));
        Future<Boolean> second = null;
        try {
            assertTrue(firstHasShopLock.await(5, TimeUnit.SECONDS));
            second = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    return tx.execute(status -> {
                        assertNotNull(shops.findByShopIdForUpdate(shopId).orElse(null));
                        ProductServiceImpl.ensureProductCapacity(
                                products.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED), 5);
                        products.saveAndFlush(product(shopId, ProductStatus.ACTIVE));
                        return true;
                    });
                } catch (org.springframework.web.server.ResponseStatusException limitReached) {
                    return false;
                }
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(250);
            assertFalse(second.isDone(), "second transaction must wait for the shop row lock");
            letFirstCommit.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertFalse(second.get(10, TimeUnit.SECONDS));
            assertEquals(5L, products.countByShopIdAndStatusNot(shopId, ProductStatus.DELETED));
        } finally {
            letFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Product product(Long shopId, ProductStatus status) {
        Product product = new Product();
        product.setShopId(shopId);
        product.setName("Quota product " + IDS.incrementAndGet());
        product.setSlug("quota-" + IDS.incrementAndGet());
        product.setStatus(status);
        product.setApprovalStatus(ApprovalStatus.PENDING);
        product.setModerationBlocked(false);
        product.setExpiryWarningDays(3);
        product.setRequiresPreparationCheck(false);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
