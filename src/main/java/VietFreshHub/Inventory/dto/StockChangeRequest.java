package VietFreshHub.Inventory.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class StockChangeRequest {
    @NotNull(message="Nhập số lượng.") private Integer quantity;
    @NotBlank(message="Nhập lý do.") @Size(max=900) private String reason;
}

