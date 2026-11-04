package VietFreshHub.subscription.dto;

import VietFreshHub.subscription.entity.SubscriptionPaymentAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPaymentForm {

    @NotNull(message = "Vui lòng chọn gói dịch vụ")
    @Positive(message = "ID gói dịch vụ không hợp lệ")
    private Long planId;

    @NotNull(message = "Vui lòng chọn thao tác đăng ký")
    private SubscriptionPaymentAction action;
}