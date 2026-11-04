package VietFreshHub.checkout.dto;

import VietFreshHub.checkout.entity.ShippingFeeSettings;
import java.math.BigDecimal;

public record ShippingFeeSettingsResponse(BigDecimal baseFee, BigDecimal includedDistanceKm,
                                          BigDecimal additionalFeePerKm, BigDecimal maxDeliveryDistanceKm) {

    public static ShippingFeeSettingsResponse from(ShippingFeeSettings settings) {
        return new ShippingFeeSettingsResponse(settings.getBaseFee(), settings.getIncludedDistanceKm(),
                settings.getAdditionalFeePerKm(), settings.getMaxDeliveryDistanceKm());
    }
}
