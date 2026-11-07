package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.notification.dto.NotificationRow;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryNotificationDestination {
    private final InventoryAlertRepository alerts;
    private final ShopService shops;

    @Transactional(readOnly = true)
    public Optional<String> resolve(NotificationRow notification, Authentication authentication) {
        boolean variant =
                "LOW_STOCK".equals(notification.type())
                        && "INVENTORY_VARIANT".equals(notification.entityType());
        boolean batch =
                ("NEAR_EXPIRY".equals(notification.type())
                                || "EXPIRED_STOCK".equals(notification.type()))
                        && "INVENTORY_BATCH".equals(notification.entityType());
        if (!variant && !batch) {
            return Optional.empty();
        }
        Long shopId = shops.getManagedShopId(authentication);
        if (notification.entityId() == null
                || !alerts.belongsToShop(shopId, notification.entityId(), batch)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Thông tin kho không còn khả dụng.");
        }
        return Optional.of(
                batch
                        ? "/seller/inventory/batches/" + notification.entityId() + "/label"
                        : "/seller/inventory?variantId=" + notification.entityId());
    }
}
