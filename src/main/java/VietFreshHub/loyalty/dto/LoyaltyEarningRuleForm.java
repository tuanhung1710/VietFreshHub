package VietFreshHub.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
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

    @NotNull(message = "Vui lòng nhập số tiền tương ứng với 1 điểm")
    @DecimalMin(
            value = "0.01",
            message = "Số tiền tương ứng với 1 điểm phải lớn hơn 0"
    )
    @Digits(
            integer = 16,
            fraction = 2,
            message = "Số tiền tối đa 16 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal earningAmount;

    private boolean earningEnabled;
}