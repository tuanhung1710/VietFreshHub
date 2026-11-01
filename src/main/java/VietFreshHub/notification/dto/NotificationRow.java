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
            case "DELIVERY_ASSIGNED", "GOODS_RETURN_CONFIRMED" -> "DELIVERY_ASSIGNMENT".equals(entityType);
            case "DELIVERY_STARTED", "DELIVERY_COMPLETED", "DELIVERY_FAILED",
                    "DELIVERY_CLAIMED", "DELIVERY_PICKED_UP" -> "DELIVERY".equals(entityType);
            case "ORDER_PLACED" -> "ORDER".equals(entityType) || "SHOP_ORDER".equals(entityType);
            case "NEW_SHOP_ORDER", "SHOP_ORDER_CONFIRMED", "SHOP_ORDER_PREPARING",
                    "SHOP_ORDER_READY", "SHOP_ORDER_CANCELLED" -> "SHOP_ORDER".equals(entityType);
            default -> false;
        };
    }
}
