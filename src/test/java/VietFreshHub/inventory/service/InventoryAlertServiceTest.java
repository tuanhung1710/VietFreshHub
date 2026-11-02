package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryAlertServiceTest {
    @Test
    void nearExpiryAndExpiredAreDifferentTransitions() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        when(repository.expiry(eq(1L), any(), eq(false))).thenReturn(List.of(new InventoryAlertRepository.Alert(8L, "Cận hạn", "Lô 8")));
        when(repository.expiry(eq(1L), any(), eq(true))).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Hết hạn", "Lô 7")));
        service.refresh(1L);
        verify(notifications).createInAppForTransition(9L, "NEAR_EXPIRY", "Cận hạn", "Lô 8", "INVENTORY_BATCH", 8L);
        verify(notifications).createInAppForTransition(9L, "EXPIRED_STOCK", "Hết hạn", "Lô 7", "INVENTORY_BATCH", 7L);
    }

    @Test
    void scansOnlyNotifyOnEntryAndRearmAfterRecovery() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        var alert = new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2");
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(alert), List.of(alert), List.of(), List.of(alert));
        service.refresh(1L);
        service.refresh(1L);
        service.refresh(1L);
        service.refresh(1L);
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }

    @Test
    void matchingEntityIdsInDifferentShopsHaveIndependentEpisodes() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(anyLong())).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(anyLong())).thenReturn(List.of(9L));
        when(repository.lowStock(anyLong(), any())).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2")));
        service.refresh(1L);
        service.refresh(2L);
        service.refresh(1L);
        service.refresh(2L);
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }

    @Test
    void notificationFailureDoesNotRememberAnUndeliveredAlert() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2")));
        doThrow(new IllegalStateException("notification failed")).doNothing().when(notifications)
                .createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
        assertThrows(IllegalStateException.class, () -> service.refresh(1L));
        service.refresh(1L);
        service.refresh(1L);
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }

    @Test
    void rollbackAfterRefreshReleasesTheShopAndAllowsRetry() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2")));
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:alertRollback" + System.nanoTime());
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        transactions.executeWithoutResult(status -> {
            service.refresh(1L);
            status.setRollbackOnly();
        });
        transactions.executeWithoutResult(status -> service.refresh(1L));
        transactions.executeWithoutResult(status -> service.refresh(1L));
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }

    @Test
    void concurrentScansInOneInstanceNotifyOnlyOnce() throws Exception {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2")));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); service.refresh(1L); return null; });
            var second = executor.submit(() -> { start.await(); service.refresh(1L); return null; });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        verify(notifications).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }

    @Test
    void newApplicationStateCanNotifyForAnExistingConditionAgain() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2")));
        new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore()).refresh(1L);
        new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore()).refresh(1L);
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
    }
}
