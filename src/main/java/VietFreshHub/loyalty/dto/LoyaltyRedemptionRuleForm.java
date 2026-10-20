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

    @NotNull(message = "Vui lòng nhập số tiền thưởng đổi tối thiểu")
    @DecimalMin(
            value = "1",
            message = "Số tiền thưởng đổi tối thiểu phải lớn hơn 0"
    )
    @Digits(integer = 16, fraction = 0, message = "Tiền thưởng phải là số đồng nguyên, tối đa 16 chữ số")
    private BigDecimal minRedemptionAmount;

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