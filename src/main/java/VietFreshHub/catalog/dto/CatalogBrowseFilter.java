package VietFreshHub.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.web.util.UriComponentsBuilder;
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
    @Positive
    private Long shopId;
    @Pattern(regexp = "newest|price_asc|price_desc|best_selling|rating")
    private String sort = "newest";
    @DecimalMin("0") @Digits(integer = 16, fraction = 0)
    private BigDecimal minPrice;
    @DecimalMin("0") @Digits(integer = 16, fraction = 0)
    private BigDecimal maxPrice;

    @AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public String urlForPage(int targetPage) {
        return url().queryParam("page", targetPage).build().encode().toUriString();
    }

    public String urlForCategory(Long targetCategory) {
        var builder = url().replaceQueryParam("categoryId");
        if (targetCategory != null) builder.queryParam("categoryId", targetCategory);
        return builder.build().encode().toUriString();
    }

    private UriComponentsBuilder url() {
        var builder = UriComponentsBuilder.fromPath("/home").queryParam("keyword", keyword);
        if (categoryId != null) builder.queryParam("categoryId", categoryId);
        if (shopId != null) builder.queryParam("shopId", shopId);
        if (minPrice != null) builder.queryParam("minPrice", minPrice);
        if (maxPrice != null) builder.queryParam("maxPrice", maxPrice);
        if (!"newest".equals(sort)) builder.queryParam("sort", sort);
        return builder;
    }
}
