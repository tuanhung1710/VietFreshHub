package VietFreshHub.notification.dto;

import java.time.LocalDateTime;

public record NotificationRow(Long notificationId, String type, String title, String content,
                              String entityType, Long entityId, LocalDateTime createdAt,
                              LocalDateTime readAt) {

    public boolean hasDetail() {
        if (entityId == null || type == null) {
            return false;
        }
        return switch (type) {
            case "LOW_STOCK" -> "INVENTORY_VARIANT".equals(entityType);
            case "NEAR_EXPIRY", "EXPIRED_STOCK" -> "INVENTORY_BATCH".equals(entityType);
            case "DELIVERY_ASSIGNED", "GOODS_RETURN_CONFIRMED" -> "DELIVERY_ASSIGNMENT".equals(entityType);
            case "DELIVERY_STARTED", "DELIVERY_COMPLETED", "DELIVERY_FAILED" -> "DELIVERY".equals(entityType);
            default -> false;
        };
    }
}
