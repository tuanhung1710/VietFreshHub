package VietFreshHub.Cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartQuantityRequest {
    @NotNull(message = "Vui lòng chọn sản phẩm.")
    @Min(value = 1, message = "Sản phẩm không hợp lệ.")
    private Long variantId;
    @NotNull(message = "Vui lòng nhập số lượng.")
    @Min(value = 0, message = "Số lượng không được âm.")
    private Integer quantity;
}
