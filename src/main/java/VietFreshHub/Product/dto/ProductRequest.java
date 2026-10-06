package VietFreshHub.Product.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;
@Getter @Setter
public class ProductRequest {
    @NotBlank(message="Nhập tên sản phẩm.") @Size(max=250) private String name;
    @Size(max=20000) private String description;
    @NotNull(message="Chọn danh mục.") private Long categoryId;
    @NotBlank(message="Chọn ảnh sản phẩm.") @Size(max=1000) private String imageUrl;
    private boolean requiresPreparationCheck;
    @Size(max=1000) private String preparationNote;
    @Valid @Size(max=20,message="Sản phẩm có tối đa 20 SKU.") private List<VariantRequest> variants=new ArrayList<>();
    @Size(max=900) private String reason;
}
