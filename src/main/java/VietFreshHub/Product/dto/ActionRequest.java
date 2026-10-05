package VietFreshHub.Product.dto;
import VietFreshHub.Product.enums.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class ActionRequest {
    @NotBlank @Size(max=900) private String reason;
    @NotNull(message="Chọn trạng thái sản phẩm hợp lệ.") private ProductStatus status;
}
