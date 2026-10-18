package VietFreshHub.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductRequest {
    @NotBlank(message = "Nhập tên sản phẩm.")
    @Size(max = 250, message = "Tên sản phẩm không được vượt quá 250 ký tự.")
    private String name;

    @Size(max = 20000, message = "Mô tả sản phẩm không được vượt quá 20.000 ký tự.")
    private String description;

    @NotNull(message = "Chọn danh mục.")
    private Long categoryId;

    @NotBlank(message = "Chọn ảnh sản phẩm hoặc dán liên kết ảnh.")
    @Size(max = 1000, message = "Liên kết ảnh sản phẩm không được vượt quá 1.000 ký tự.")
    private String imageUrl;

    private boolean requiresPreparationCheck;

    @Size(max = 1000, message = "Hướng dẫn kiểm tra và đóng gói không được vượt quá 1.000 ký tự.")
    private String preparationNote;

    @NotNull(message = "Nhập số ngày nhắc trước hạn sử dụng.")
    @Min(value = 0, message = "Số ngày nhắc trước hạn sử dụng phải là số nguyên không âm.")
    private Integer expiryWarningDays = 3;

    @Valid
    @Size(max = 20, message = "Mỗi sản phẩm có tối đa 20 phân loại.")
    private List<VariantRequest> variants = new ArrayList<>();

    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
