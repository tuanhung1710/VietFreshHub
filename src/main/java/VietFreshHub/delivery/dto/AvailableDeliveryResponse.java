package VietFreshHub.delivery.dto;

import java.math.BigDecimal;

public record AvailableDeliveryResponse(
        Long deliveryId,
        Long shopOrderId,
        String orderCode,
        String shopName,
        DeliveryLocationResponse pickupLocation,
        DeliveryLocationResponse deliveryLocation,
        BigDecimal shippingDistanceMeters) {
}
