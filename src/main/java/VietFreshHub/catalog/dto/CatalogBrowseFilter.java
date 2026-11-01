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
    @jakarta.validation.constraints.Pattern(regexp = "all|available|unavailable")
    private String availability = "all";
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 0)
    private java.math.BigDecimal minPrice;
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 0)
    private java.math.BigDecimal maxPrice;

    @jakarta.validation.constraints.AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
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

    private org.springframework.web.util.UriComponentsBuilder url() {
        var builder = org.springframework.web.util.UriComponentsBuilder.fromPath("/home").queryParam("keyword", keyword);
        if (categoryId != null) builder.queryParam("categoryId", categoryId);
        if (minPrice != null) builder.queryParam("minPrice", minPrice);
        if (maxPrice != null) builder.queryParam("maxPrice", maxPrice);
        if (!"all".equals(availability)) builder.queryParam("availability", availability);
        return builder;
    }
}
