package VietFreshHub.order.dto;

import VietFreshHub.order.service.OrderLabels;

public record CustomerShopOrderStatus(Long orderId, Long shopOrderId, String shopName, String status) {
    public String statusLabel() {
        return OrderLabels.customerStatus(status);
    }
}
