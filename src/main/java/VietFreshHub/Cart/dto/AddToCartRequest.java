package VietFreshHub.Cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "Vui lòng chọn sản phẩm.")
    @Min(value = 1, message = "Sản phẩm không hợp lệ.")
    private Long variantId;

    @NotNull(message = "Vui lòng nhập số lượng.")
    @Min(value = 1, message = "Số lượng phải từ 1 trở lên.")
    private Integer quantity;
}
