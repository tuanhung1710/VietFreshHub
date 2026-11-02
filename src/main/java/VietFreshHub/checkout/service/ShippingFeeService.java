package VietFreshHub.checkout.service;

import VietFreshHub.exception.CheckoutException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class ShippingFeeService {
    private static final BigDecimal BASE_FEE = new BigDecimal("15000");
    private static final BigDecimal INCLUDED_DISTANCE_METERS = new BigDecimal("2000");
    private static final BigDecimal ADDITIONAL_FEE_PER_METER = new BigDecimal("5");
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("1000");

    public BigDecimal calculateShippingFee(BigDecimal distanceMeters) {
        if (distanceMeters == null) {
            throw new CheckoutException("Khoảng cách giao hàng không được để trống.");
        }
        if (distanceMeters.signum() < 0) {
            throw new CheckoutException("Khoảng cách giao hàng không được âm.");
        }

        BigDecimal additionalMeters = distanceMeters.subtract(INCLUDED_DISTANCE_METERS).max(BigDecimal.ZERO);
        BigDecimal rawFee = BASE_FEE.add(additionalMeters.multiply(ADDITIONAL_FEE_PER_METER));
        BigDecimal finalFee = rawFee.divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
                .multiply(ROUNDING_UNIT)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (finalFee.precision() > 18) {
            throw new CheckoutException("Phí giao hàng vượt giới hạn lưu trữ DECIMAL(18,2).");
        }
        return finalFee;
    }
}
