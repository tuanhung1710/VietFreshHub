package VietFreshHub.admin.dto;

import VietFreshHub.checkout.entity.ShippingFeeSettings;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminShippingFeeRequest {

    @NotNull(message = "Vui lòng nhập phí mở cửa.")
    @DecimalMin(value = "0", message = "Phí mở cửa không được âm.")
    @Digits(integer = 16, fraction = 0, message = "Phí mở cửa phải là số đồng nguyên, tối đa 16 chữ số.")
    private BigDecimal baseFee;

    @NotNull(message = "Vui lòng nhập số km đầu tiên.")
    @DecimalMin(value = "0", message = "Số km đầu tiên không được âm.")
    @Digits(integer = 8, fraction = 3, message = "Số km đầu tiên tối đa 8 chữ số nguyên và 3 chữ số thập phân.")
    private BigDecimal includedDistanceKm;

    @NotNull(message = "Vui lòng nhập phí cho mỗi km tiếp theo.")
    @DecimalMin(value = "0", message = "Phí cho mỗi km tiếp theo không được âm.")
    @Digits(integer = 16, fraction = 0, message = "Phí mỗi km phải là số đồng nguyên, tối đa 16 chữ số.")
    private BigDecimal additionalFeePerKm;

    @NotNull(message = "Vui lòng nhập phạm vi giao hàng.")
    @DecimalMin(value = "0.001", message = "Phạm vi giao hàng phải lớn hơn 0 km.")
    @Digits(integer = 8, fraction = 3, message = "Phạm vi giao hàng tối đa 8 chữ số nguyên và 3 chữ số thập phân.")
    private BigDecimal maxDeliveryDistanceKm;

    public static AdminShippingFeeRequest from(ShippingFeeSettings settings) {
        AdminShippingFeeRequest request = new AdminShippingFeeRequest();
        request.setBaseFee(settings.getBaseFee().setScale(0));
        request.setIncludedDistanceKm(normalizeDistance(settings.getIncludedDistanceKm()));
        request.setAdditionalFeePerKm(settings.getAdditionalFeePerKm().setScale(0));
        request.setMaxDeliveryDistanceKm(normalizeDistance(settings.getMaxDeliveryDistanceKm()));
        return request;
    }

    private static BigDecimal normalizeDistance(BigDecimal distance) {
        BigDecimal normalized = distance.stripTrailingZeros();
        return normalized.setScale(Math.max(0, normalized.scale()));
    }
}
