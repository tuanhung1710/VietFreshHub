package VietFreshHub.order.dto;

import java.util.List;

/** Page-scoped presentation data; monetary totals and primary stage stay in OrderListRow. */
public record OrderHistorySummary(List<Product> products, int productCount, long unitCount,
        List<Shop> shops, List<OrderDetailsResponse.PaymentInfo> payments, int paymentCount,
        boolean cancellable, boolean reorderable, boolean trackable) {
    public OrderHistorySummary {
        products = List.copyOf(products); shops = List.copyOf(shops); payments = List.copyOf(payments);
    }
    public record Product(String productName, String variantName, int quantity, String imageUrl) {}
    public record Shop(String name, String fulfillmentLabel, String deliveryStatus, String deliveryLabel) {}
}
