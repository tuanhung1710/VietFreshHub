package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockChangeRequest {
    /** Chênh lệch do biểu mẫu điều chỉnh cũ gửi. */
    private Integer quantity;

    @Min(value = 0, message = "Tồn sau kiểm đếm không được âm.")
    private Integer countedQuantity;

    @Min(0)
    private Integer expectedQuantity;

    @NotBlank(message = "Vui lòng nhập lý do.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
