package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockChangeRequest {
    @NotNull(message = "Vui lòng nhập số lượng.")
    private Integer quantity;

    @NotBlank(message = "Vui lòng nhập lý do.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
