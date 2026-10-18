package VietFreshHub.checkout.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record ShopShippingQuote(Long shopId, BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                                BigDecimal destinationLatitude, BigDecimal destinationLongitude,
                                BigDecimal distanceMeters, BigDecimal shippingFee) implements Serializable {
}
