package VietFreshHub.order.dto;

import java.math.BigDecimal;

public record ShopSalesSummary(BigDecimal productSubtotal, BigDecimal discounts,
                               BigDecimal shippingFees, Long completedOrders) {
}
