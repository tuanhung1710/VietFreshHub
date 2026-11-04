package VietFreshHub.inventory.service;

import VietFreshHub.inventory.enums.InventoryAlertType;
import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Quét cảnh báo theo cửa hàng và chỉ thông báo khi một SKU/lô bắt đầu một đợt cảnh báo mới. */
@Service
@RequiredArgsConstructor
public class InventoryAlertService {
    private final InventoryAlertRepository inventoryAlertRepository;
    private final ShopRepository shopRepository;
    private final ShopService shopService;
    private final NotificationService notificationService;
    private final InventoryAlertStateStore alertStateStore;

    @Transactional
    public void refresh(Long shopId) {
        alertStateStore.reconcile(shopId, previous -> scanShopAlerts(shopId, previous));
    }

    private Map<InventoryAlertType, Set<Long>> scanShopAlerts(
            Long shopId, Map<InventoryAlertType, Set<Long>> previous) {
        var shop = shopRepository.findByShopIdForUpdate(shopId).orElse(null);
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            return previous;
        }
        List<Long> recipients = shopService.findActiveManagerUserIds(shopId);
        if (recipients.isEmpty()) {
            return previous;
        }
        var today = InventoryRules.today();
        var current =
                new EnumMap<InventoryAlertType, List<InventoryAlertRepository.Alert>>(
                        InventoryAlertType.class);
        current.put(InventoryAlertType.LOW_STOCK, inventoryAlertRepository.lowStock(shopId, today));
        current.put(
                InventoryAlertType.NEAR_EXPIRY,
                inventoryAlertRepository.expiry(shopId, today, false));
        current.put(
                InventoryAlertType.EXPIRED_STOCK,
                inventoryAlertRepository.expiry(shopId, today, true));
        var next = new EnumMap<InventoryAlertType, Set<Long>>(InventoryAlertType.class);
        current.forEach(
                (type, matches) ->
                        next.put(
                                type,
                                notifyNew(
                                        type,
                                        matches,
                                        previous.getOrDefault(type, Set.of()),
                                        recipients)));
        return next;
    }

    /** Lưu đủ ID đang có cảnh báo; bỏ ID đã thông báo để tránh gửi lặp ở lần quét kế tiếp. */
    private Set<Long> notifyNew(
            InventoryAlertType type,
            List<InventoryAlertRepository.Alert> current,
            Set<Long> previous,
            List<Long> recipients) {
        Set<Long> currentIds = new HashSet<>();
        for (var alert : current) {
            if (!currentIds.add(alert.entityId()) || previous.contains(alert.entityId())) {
                continue;
            }
            for (Long userId : recipients) {
                notificationService.createInAppForTransition(
                        userId,
                        type.name(),
                        alert.title(),
                        alert.content(),
                        type.entityType(),
                        alert.entityId());
            }
        }
        return currentIds;
    }
}
