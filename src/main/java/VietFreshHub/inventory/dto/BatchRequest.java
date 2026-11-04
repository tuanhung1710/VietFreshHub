package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchRequest extends BatchMetadataRequest {
    @NotNull(message = "Vui lòng nhập số lượng.")
    @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0.")
    private Integer quantity;
}
