package VietFreshHub.product.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Bộ lọc danh sách sản phẩm của người bán; màn hình kho dùng bộ lọc riêng. */
@Getter
@Setter
public class ProductCatalogFilter {
    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
    private String q = "";

    @Positive(message = "Danh mục không hợp lệ.")
    private Long category;

    private String tab = "";

    private String sort = "updated_desc";
}
