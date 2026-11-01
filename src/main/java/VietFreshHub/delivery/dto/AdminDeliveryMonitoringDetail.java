package VietFreshHub.delivery.dto;

import VietFreshHub.order.dto.OrderProcessingHistoryEntry;

import java.util.List;

public record AdminDeliveryMonitoringDetail(
        DeliveryMonitoringDetail deliveryDetail,
        String shopPhone,
        String shopAddress,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        List<OrderProcessingHistoryEntry> orderHistory) {
}
