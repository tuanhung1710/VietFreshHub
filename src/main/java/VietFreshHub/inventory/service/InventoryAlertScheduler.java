package VietFreshHub.inventory.service;

import VietFreshHub.shop.repository.ShopRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "inventory.alerts.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InventoryAlertScheduler {
    private final ShopRepository shops;
    private final InventoryAlertService alerts;

    @Scheduled(
            initialDelayString = "${inventory.alerts.initial-delay-ms:60000}",
            fixedDelayString = "${inventory.alerts.scan-delay-ms:300000}")
    public void scan() {
        try {
            int page = 0;
            org.springframework.data.domain.Page<VietFreshHub.shop.entity.Shop> result;
            do {
                result =
                        shops.findByStatus(
                                "ACTIVE", PageRequest.of(page++, 100, Sort.by("shopId")));
                for (var shop : result.getContent()) {
                    try {
                        alerts.refresh(shop.getShopId());
                    } catch (RuntimeException exception) {
                        log.error(
                                "Không thể cập nhật cảnh báo kho của shop {}",
                                shop.getShopId(),
                                exception);
                    }
                }
            } while (result.hasNext());
        } catch (RuntimeException exception) {
            log.error("Không thể quét cảnh báo kho", exception);
        }
    }
}
