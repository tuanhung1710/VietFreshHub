package VietFreshHub.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import VietFreshHub.common.PageSizes;
import VietFreshHub.common.Pagination;

@Getter
@Setter
public class CatalogBrowseFilter {
    @Min(1) @Max(Pagination.MAX_PAGE)
    private int page = 1;
    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
    private String keyword = "";
    @Positive
    private Long categoryId;
    @Positive
    private Long shopId;
    @Size(max = 100)
    private List<@Positive Long> shopIds = List.of();
    @Size(max = 100)
    private List<@NotBlank @Size(max = 100) String> places = List.of();
    @Min(0) @Max(5)
    private int minRating;
    private int pageSize = PageSizes.DEFAULT;
    @Pattern(regexp = "popular|latest|sales|asc|desc|newest|price_asc|price_desc|best_selling|rating")
    private String sort = "popular";
    @DecimalMin("0") @Digits(integer = 16, fraction = 0)
    private BigDecimal minPrice;
    @DecimalMin("0") @Digits(integer = 16, fraction = 0)
    private BigDecimal maxPrice;

    @AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    @AssertTrue(message = "Số sản phẩm mỗi trang không hợp lệ.")
    public boolean isPageSizeAllowed() {
        return PageSizes.isAllowed(pageSize);
    }

}
