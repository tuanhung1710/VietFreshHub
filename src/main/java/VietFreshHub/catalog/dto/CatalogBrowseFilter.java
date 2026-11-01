package VietFreshHub.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogBrowseFilter {
    @Min(1) @Max(10000)
    private int page = 1;
    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
    private String keyword = "";
    @Positive
    private Long categoryId;
}
