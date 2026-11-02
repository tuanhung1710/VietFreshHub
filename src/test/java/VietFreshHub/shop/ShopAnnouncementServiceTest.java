package VietFreshHub.shop;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopAnnouncementService;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ShopAnnouncementServiceTest {
    @Test
    void receivesStableSourceIdAndOnlyAddressesActiveShopManagers() {
        var shops = mock(ShopService.class);
        var repository = mock(ShopRepository.class);
        var notifications = mock(NotificationService.class);
        var service = new ShopAnnouncementService(shops, repository, notifications,
                mock(NotificationRepository.class), mock(AuthService.class));
        when(repository.findByShopIdForUpdate(1L)).thenReturn(Optional.of(new Shop()));
        when(shops.findActiveManagerUserIds(1L)).thenReturn(List.of(7L));
        service.receive(1L, ShopAnnouncementService.Origin.ADMIN, 9L, " Bảo trì ", " Nội dung ");
        verify(notifications).createInApp(7L, "ADMIN_ANNOUNCEMENT", "Bảo trì", "Nội dung", "ANNOUNCEMENT", 9L);
        assertThrows(IllegalArgumentException.class,
                () -> service.receive(1L, ShopAnnouncementService.Origin.SYSTEM, null, "Tiêu đề", "Nội dung"));
    }
}
