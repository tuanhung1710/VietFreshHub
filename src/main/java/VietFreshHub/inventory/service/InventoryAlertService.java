package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.inventory.enums.InventoryAlertType;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {
    private final InventoryAlertRepository alerts;
    private final ShopRepository shops;
    private final ShopService ownership;
    private final NotificationService notifications;
    private final InventoryAlertStateStore states;

    @Transactional
    public void refresh(Long shopId) {
        states.reconcile(shopId, previous -> refresh(shopId, previous));
    }

    private Map<InventoryAlertType, Set<Long>> refresh(Long shopId,
                                                      Map<InventoryAlertType, Set<Long>> previous) {
        var shop = shops.findByShopIdForUpdate(shopId).orElse(null);
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) return previous;
        var recipients = ownership.findActiveManagerUserIds(shopId);
        if (recipients.isEmpty()) return previous;
        var today = InventoryRules.today();
        var current = new EnumMap<InventoryAlertType, List<InventoryAlertRepository.Alert>>(InventoryAlertType.class);
        current.put(InventoryAlertType.LOW_STOCK, alerts.lowStock(shopId, today));
        current.put(InventoryAlertType.NEAR_EXPIRY, alerts.expiry(shopId, today, false));
        current.put(InventoryAlertType.EXPIRED_STOCK, alerts.expiry(shopId, today, true));
        var next = new EnumMap<InventoryAlertType, Set<Long>>(InventoryAlertType.class);
        current.forEach((type, matches) -> next.put(type,
                notifyNew(type, matches, previous.getOrDefault(type, Set.of()), recipients)));
        return next;
    }

    private Set<Long> notifyNew(InventoryAlertType type, List<InventoryAlertRepository.Alert> current,
                                Set<Long> previous, List<Long> recipients) {
        Set<Long> currentIds = new HashSet<>();
        for (var alert : current) {
            if (!currentIds.add(alert.entityId()) || previous.contains(alert.entityId())) continue;
            for (Long userId : recipients) notifications.createInAppForTransition(userId, type.name(), alert.title(),
                    alert.content(), type.entityType(), alert.entityId());
        }
        return currentIds;
    }
}
