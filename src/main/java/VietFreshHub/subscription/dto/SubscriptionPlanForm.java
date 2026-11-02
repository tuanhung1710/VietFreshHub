package VietFreshHub.subscription.dto;

import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.entity.PlanStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlanForm {

    @NotBlank(message = "Tên gói không được để trống")
    @Size(max = 100, message = "Tên gói tối đa 100 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng nhập giá gói")
    @DecimalMin(value = "0.00", message = "Giá gói không được âm")
    @Digits(integer = 16, fraction = 2, message = "Giá gói tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal price;

    @NotNull(message = "Vui lòng chọn chu kỳ")
    private BillingPeriod billingPeriod;

    private String benefits;

    @NotNull(message = "Vui lòng nhập giới hạn sản phẩm public")
    @Min(value = 1, message = "Giới hạn sản phẩm phải lớn hơn 0")
    private Integer maxProducts;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private PlanStatus status = PlanStatus.ACTIVE;
}