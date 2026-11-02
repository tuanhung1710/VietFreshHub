package VietFreshHub.admin.dto;

import VietFreshHub.product.entity.Category;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class AdminCategoryRequest {

    private Long categoryId;

    @NotBlank(message = "Tên danh mục không được để trống.")
    @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự.")
    private String name;

    @Size(max = 200, message = "Slug tối đa 200 ký tự.")
    @Pattern(
            regexp = "(?i)(?:[a-z0-9]+(?:-[a-z0-9]+)*)?",
            message = "Slug chỉ được gồm chữ cái không dấu, số và dấu gạch ngang.")
    private String slug;

    @Size(max = 1000, message = "Mô tả tối đa 1.000 ký tự.")
    private String description;

    @Positive(message = "Danh mục cha không hợp lệ.")
    private Long parentCategoryId;

    private MultipartFile imageFile;

    private boolean removeImage;

    public static AdminCategoryRequest from(Category category) {
        AdminCategoryRequest request = new AdminCategoryRequest();
        request.setCategoryId(category.getCategoryId());
        request.setName(category.getName());
        request.setSlug(category.getSlug());
        request.setDescription(category.getDescription());
        request.setParentCategoryId(category.getParentCategoryId());
        return request;
    }
}
