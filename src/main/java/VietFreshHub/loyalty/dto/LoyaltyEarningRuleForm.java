package VietFreshHub.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LoyaltyEarningRuleForm {

    @NotNull(message = "Vui lòng nhập tỷ lệ tích thưởng (%)")
    @DecimalMin(
            value = "0.01",
            message = "Tỷ lệ tích thưởng (%) phải lớn hơn 0"
    )
    @Digits(
            integer = 3,
            fraction = 2,
            message = "Tỷ lệ có tối đa 2 chữ số thập phân"
    )
    @DecimalMax(value = "100.00", message = "Tỷ lệ tích thưởng không được vượt 100%")
    private BigDecimal earningPercent;

    private boolean earningEnabled;
}