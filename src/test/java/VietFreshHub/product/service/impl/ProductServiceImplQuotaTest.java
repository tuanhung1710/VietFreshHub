package VietFreshHub.product.service.impl;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.PriceHistoryRepository;
import VietFreshHub.product.repository.ProductCategoryRepository;
import VietFreshHub.product.repository.ProductImageRepository;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.product.service.ProductCatalogQueries;
import VietFreshHub.product.service.ProductCatalogReader;
import VietFreshHub.product.validator.CategoryValidator;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.dto.SubscriptionPlanResponse;
import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.entity.SubscriptionStatus;
import VietFreshHub.subscription.services.ShopSubscriptionService;
import VietFreshHub.subscription.services.SubscriptionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplQuotaTest {
    private static final Long SHOP_ID = 7L;

    @Mock ProductCatalogQueries catalogQueries;
    @Mock ProductCatalogReader catalogReader;
    @Mock ProductRepository products;
    @Mock ProductVariantRepository variants;
    @Mock CategoryRepository categories;
    @Mock ProductCategoryRepository productCategories;
    @Mock ProductImageRepository productImages;
    @Mock PriceHistoryRepository priceHistories;
    @Mock InventoryBatchRepository batches;
    @Mock InventoryTransactionRepository inventoryTransactions;
    @Mock InventoryJournalService inventoryJournal;
    @Mock InventoryLockHelper inventoryLocks;
    @Mock ShopService shops;
    @Mock ShopRepository shopRepository;
    @Mock ShopSubscriptionService subscriptions;
    @Mock SubscriptionPlanService plans;
    @Mock AuthService auth;
    @Mock CategoryValidator categoryValidator;
    @Mock ProductAvailabilityService availability;

    @InjectMocks ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        when(shopRepository.findByShopIdForUpdate(SHOP_ID)).thenReturn(Optional.of(new Shop()));
    }

    @Test
    void activePaidSubscriptionAllowsCreationBelowItsLimit() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.of(subscription(8)));
        when(products.countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED))
                .thenReturn(7L);

        assertDoesNotThrow(() -> service.enforceProductQuota(SHOP_ID));

        verify(plans, never()).getFreePlan();
        verify(products).countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED);
    }

    @Test
    void activePaidSubscriptionRejectsCreationAtItsLimit() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.of(subscription(8)));
        when(products.countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED))
                .thenReturn(8L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.enforceProductQuota(SHOP_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(plans, never()).getFreePlan();
    }

    @Test
    void missingSubscriptionUsesFreePlan() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.empty());
        when(plans.getFreePlan()).thenReturn(freePlan(5, PlanStatus.ACTIVE));
        when(products.countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED))
                .thenReturn(4L);

        assertDoesNotThrow(() -> service.enforceProductQuota(SHOP_ID));

        verify(plans).getFreePlan();
    }

    @Test
    void expiredSubscriptionUsesFreePlanWhenNoCurrentSubscriptionIsReturned() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.empty());
        when(plans.getFreePlan()).thenReturn(freePlan(5, PlanStatus.ACTIVE));
        when(products.countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED))
                .thenReturn(4L);

        assertDoesNotThrow(() -> service.enforceProductQuota(SHOP_ID));

        verify(plans).getFreePlan();
    }

    @Test
    void cancelledSubscriptionUsesFreePlanWhenNoCurrentSubscriptionIsReturned() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.empty());
        when(plans.getFreePlan()).thenReturn(freePlan(5, PlanStatus.ACTIVE));
        when(products.countByShopIdAndStatusNot(SHOP_ID, VietFreshHub.product.enums.ProductStatus.DELETED))
                .thenReturn(4L);

        assertDoesNotThrow(() -> service.enforceProductQuota(SHOP_ID));

        verify(plans).getFreePlan();
    }

    @Test
    void missingFreePlanFailsClosed() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.empty());
        when(plans.getFreePlan()).thenThrow(new IllegalStateException("Free plan missing"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.enforceProductQuota(SHOP_ID));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(products, never()).countByShopIdAndStatusNot(SHOP_ID,
                VietFreshHub.product.enums.ProductStatus.DELETED);
    }

    @Test
    void inactiveFreePlanFailsClosed() {
        when(subscriptions.getCurrentSubscription(SHOP_ID)).thenReturn(Optional.empty());
        when(plans.getFreePlan()).thenReturn(freePlan(5, PlanStatus.INACTIVE));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.enforceProductQuota(SHOP_ID));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(products, never()).countByShopIdAndStatusNot(SHOP_ID,
                VietFreshHub.product.enums.ProductStatus.DELETED);
    }

    private ShopSubscriptionResponse subscription(int maxProducts) {
        return new ShopSubscriptionResponse(1L, SHOP_ID, "Shop", 2L, "Paid", BillingPeriod.MONTHLY,
                null, maxProducts, null, null, SubscriptionStatus.ACTIVE, false, null, null);
    }

    private SubscriptionPlanResponse freePlan(int maxProducts, PlanStatus status) {
        return new SubscriptionPlanResponse(1L, "Free", null, BigDecimal.ZERO, BillingPeriod.MONTHLY,
                null, maxProducts, status, null, null);
    }
}
