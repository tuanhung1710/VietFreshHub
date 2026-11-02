package VietFreshHub.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Chỉ nhận lý do xóa; trạng thái đích được quyết định ở service. */
@Getter
@Setter
public class VariantDeleteRequest {
    @NotBlank(message = "Nhập lý do xóa phân loại.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
