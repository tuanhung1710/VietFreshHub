package VietFreshHub.checkout.service;

import VietFreshHub.checkout.dto.ShippingFeeSettingsResponse;
import VietFreshHub.checkout.repository.ShippingFeeSettingsRepository;
import VietFreshHub.exception.CheckoutException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingFeeService {

    private static final BigDecimal METERS_PER_KM = new BigDecimal("1000");
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("1000");

    private final ShippingFeeSettingsRepository shippingFeeSettingsRepository;

    @Transactional(readOnly = true)
    public ShippingFeeSettingsResponse getSettings() {
        return shippingFeeSettingsRepository.findById(1L)
                .map(ShippingFeeSettingsResponse::from)
                .orElseThrow(() -> new CheckoutException("Chưa cấu hình phí vận chuyển. Vui lòng liên hệ quản trị viên."));
    }

    public BigDecimal calculateShippingFee(BigDecimal distanceMeters) {
        return calculateShippingFee(distanceMeters, getSettings());
    }

    public BigDecimal calculateShippingFee(BigDecimal distanceMeters, ShippingFeeSettingsResponse settings) {
        if (distanceMeters == null) {
            throw new CheckoutException("Khoảng cách giao hàng không được để trống.");
        }
        if (distanceMeters.signum() < 0) {
            throw new CheckoutException("Khoảng cách giao hàng không được âm.");
        }

        BigDecimal additionalKm = distanceMeters.divide(METERS_PER_KM)
                .subtract(settings.includedDistanceKm()).max(BigDecimal.ZERO);
        BigDecimal rawFee = settings.baseFee().add(additionalKm.multiply(settings.additionalFeePerKm()));
        BigDecimal finalFee = rawFee.divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
                .multiply(ROUNDING_UNIT)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (finalFee.precision() > 18) {
            throw new CheckoutException("Phí giao hàng vượt giới hạn lưu trữ DECIMAL(18,2).");
        }
        return finalFee;
    }
}
