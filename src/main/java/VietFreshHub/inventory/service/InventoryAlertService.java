package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {
    private final InventoryAlertRepository alerts;
    private final ShopRepository shops;
    private final ShopService ownership;
    private final NotificationService notifications;

    @Transactional
    public void refresh(Long shopId) {
        // One shop lock serializes scans from multiple application instances.
        var shop = shops.findByShopIdForUpdate(shopId).orElse(null);
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) return;
        var recipients = ownership.findActiveManagerUserIds(shopId);
        if (recipients.isEmpty()) return;
        reconcile(shopId, "LOW_STOCK", "INVENTORY_VARIANT", alerts.lowStock(shopId, InventoryRules.today()), recipients);
    }

    private void reconcile(Long shopId, String type, String entityType,
                           List<InventoryAlertRepository.Alert> current, List<Long> recipients) {
        Set<Long> previous = alerts.activeIds(shopId, type);
        Set<Long> currentIds = current.stream().map(InventoryAlertRepository.Alert::entityId).collect(Collectors.toSet());
        for (Long entityId : previous) {
            if (!currentIds.contains(entityId)) alerts.setActive(shopId, type, entityId, false);
        }
        for (var alert : current) {
            if (previous.contains(alert.entityId())) continue;
            alerts.setActive(shopId, type, alert.entityId(), true);
            for (Long userId : recipients) notifications.createInAppForTransition(userId, type, alert.title(),
                    alert.content(), entityType, alert.entityId());
        }
    }
}
