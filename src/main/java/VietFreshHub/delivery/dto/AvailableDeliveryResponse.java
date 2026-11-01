package VietFreshHub.delivery.dto;

public record AvailableDeliveryResponse(
        Long deliveryId,
        String orderCode,
        String shopName,
        DeliveryLocationResponse pickupLocation,
        DeliveryLocationResponse deliveryLocation) {
}
