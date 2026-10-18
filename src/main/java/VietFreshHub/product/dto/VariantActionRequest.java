package VietFreshHub.product.dto;

import VietFreshHub.product.enums.VariantStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VariantActionRequest {
    @NotBlank(message = "Nhập lý do thay đổi.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;

    @NotNull(message = "Chọn trạng thái phân loại hợp lệ.")
    private VariantStatus status;
}
