package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryAlertServiceTest {
    @Test
    void scansOnlyNotifyOnEntryAndRearmAfterRecovery() {
        var repository = mock(InventoryAlertRepository.class);
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications);
        when(shops.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(1L)).thenReturn(List.of(9L));
        var alert = new InventoryAlertRepository.Alert(7L, "Tồn thấp", "Còn 2");
        when(repository.lowStock(eq(1L), any())).thenReturn(List.of(alert), List.of(alert), List.of(), List.of(alert));
        when(repository.activeIds(1L, "LOW_STOCK")).thenReturn(Set.of(), Set.of(7L), Set.of(7L), Set.of());
        service.refresh(1L);
        service.refresh(1L);
        service.refresh(1L);
        service.refresh(1L);
        verify(notifications, times(2)).createInAppForTransition(9L, "LOW_STOCK", "Tồn thấp", "Còn 2", "INVENTORY_VARIANT", 7L);
        verify(repository).setActive(1L, "LOW_STOCK", 7L, false);
    }
}
