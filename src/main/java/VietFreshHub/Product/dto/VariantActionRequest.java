package VietFreshHub.Product.dto;

import VietFreshHub.Product.enums.VariantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VariantActionRequest {
    @NotBlank @Size(max=900) private String reason;
    @NotNull(message="Chọn trạng thái SKU hợp lệ.") private VariantStatus status;
}
