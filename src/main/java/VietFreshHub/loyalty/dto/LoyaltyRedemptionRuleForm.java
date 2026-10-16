package VietFreshHub.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LoyaltyRedemptionRuleForm {

    @NotNull(message = "Vui lòng nhập giá trị giảm của 1 điểm")
    @DecimalMin(
            value = "0.01",
            message = "Giá trị giảm của 1 điểm phải lớn hơn 0"
    )
    @Digits(
            integer = 16,
            fraction = 2,
            message = "Giá trị giảm tối đa 16 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal redemptionValue;

    @NotNull(message = "Vui lòng nhập số điểm đổi tối thiểu")
    @Min(
            value = 1,
            message = "Số điểm đổi tối thiểu phải lớn hơn 0"
    )
    private Integer minRedemptionPoints;

    @NotNull(message = "Vui lòng nhập phần trăm giảm tối đa")
    @Min(
            value = 1,
            message = "Phần trăm giảm tối đa phải từ 1 đến 100"
    )
    @Max(
            value = 100,
            message = "Phần trăm giảm tối đa phải từ 1 đến 100"
    )
    private Integer maxRedemptionPercent;

    private boolean redemptionEnabled;
}