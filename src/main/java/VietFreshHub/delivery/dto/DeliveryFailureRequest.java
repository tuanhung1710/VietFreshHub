package VietFreshHub.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryFailureRequest {

    @NotBlank(message = "Vui lòng nhập lý do giao hàng thất bại.")
    @Size(max = 1000, message = "Lý do giao hàng thất bại tối đa 1.000 ký tự.")
    private String failureReason;
}
