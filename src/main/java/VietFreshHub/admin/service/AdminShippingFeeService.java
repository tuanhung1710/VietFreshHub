package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminShippingFeeRequest;
import VietFreshHub.checkout.dto.ShippingFeeSettingsResponse;
import VietFreshHub.checkout.entity.ShippingFeeSettings;
import VietFreshHub.checkout.repository.ShippingFeeSettingsRepository;
import VietFreshHub.checkout.service.ShippingFeeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShippingFeeService {

    private final ShippingFeeSettingsRepository shippingFeeSettingsRepository;
    private final ShippingFeeService shippingFeeService;
    private final Validator validator;

    public AdminShippingFeeRequest getForm() {
        return AdminShippingFeeRequest.from(findSettings());
    }

    @Transactional
    public void update(AdminShippingFeeRequest form) {
        validateForm(form);

        ShippingFeeSettings settings = findSettings();
        settings.setBaseFee(form.getBaseFee());
        settings.setIncludedDistanceKm(form.getIncludedDistanceKm());
        settings.setAdditionalFeePerKm(form.getAdditionalFeePerKm());
        settings.setMaxDeliveryDistanceKm(form.getMaxDeliveryDistanceKm());
        shippingFeeSettingsRepository.saveAndFlush(settings);
    }

    private ShippingFeeSettings findSettings() {
        return shippingFeeSettingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình phí vận chuyển. Cần khởi tạo bản ghi shipping_fee_settings có ID = 1."));
    }

    private void validateForm(AdminShippingFeeRequest form) {
        if (form == null) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông số phí vận chuyển.");
        }
        Set<ConstraintViolation<AdminShippingFeeRequest>> violations = validator.validate(form);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(ConstraintViolation::getMessage).sorted().findFirst()
                    .orElse("Thông số phí vận chuyển không hợp lệ."));
        }
        if (form.getIncludedDistanceKm().compareTo(form.getMaxDeliveryDistanceKm()) > 0) {
            throw new IllegalArgumentException("Số km đầu tiên không được vượt quá phạm vi giao hàng.");
        }
        ShippingFeeSettingsResponse settings = new ShippingFeeSettingsResponse(form.getBaseFee(),
                form.getIncludedDistanceKm(), form.getAdditionalFeePerKm(), form.getMaxDeliveryDistanceKm());
        BigDecimal maxDistanceMeters = form.getMaxDeliveryDistanceKm().multiply(new BigDecimal("1000"));
        shippingFeeService.calculateShippingFee(maxDistanceMeters, settings);
    }
}
