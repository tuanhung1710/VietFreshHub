package VietFreshHub.shop;

import VietFreshHub.shop.dto.ShopProfileRequest;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopProfileService;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopProfileServiceTest {
    private final ShopService ownership = mock(ShopService.class);
    private final ShopRepository repository = mock(ShopRepository.class);
    private final ShopProfileService service = new ShopProfileService(ownership, repository);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("owner", "");
    private Shop shop;

    @BeforeEach
    void setUp() {
        shop = new Shop();
        when(ownership.getManagedShopId(authentication)).thenReturn(7L);
        when(repository.findByShopIdForUpdate(7L)).thenReturn(Optional.of(shop));
    }

    @Test
    void updateOnlyChangesOwnedProfileFields() {
        var request = new ShopProfileRequest();
        request.setShopName("  Trái cây Sơn  ");
        request.setDescription(" ");
        service.update(authentication, request);
        assertEquals("Trái cây Sơn", shop.getShopName());
        assertNull(shop.getDescription());
        assertEquals("ACTIVE", shop.getStatus());
        verify(repository).findByShopIdForUpdate(7L);
    }

    @Test
    void closeShopAndRejectUnknownOrSuspendedStatus() {
        service.setAvailability(authentication, "CLOSED");
        assertEquals("CLOSED", shop.getAvailabilityStatus());
        assertThrows(IllegalArgumentException.class, () -> service.setAvailability(authentication, "ACTIVE"));
        shop.setStatus("SUSPENDED");
        assertThrows(IllegalArgumentException.class, () -> service.setAvailability(authentication, "OPEN"));
        assertEquals("CLOSED", shop.getAvailabilityStatus());
    }
}
