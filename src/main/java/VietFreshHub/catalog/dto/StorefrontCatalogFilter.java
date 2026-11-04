package VietFreshHub.catalog.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Điều kiện tìm sản phẩm của khách hàng; page và size được nhận riêng tại controller. */
@Getter
@Setter
public class StorefrontCatalogFilter {
    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
    private String q = "";

    @Positive private Long category;

    @Size(max = 100)
    private List<@Positive Long> shopIds = List.of();

    @Size(max = 100)
    private List<@NotBlank @Size(max = 100) String> places = List.of();

    @Min(0)
    @Max(5)
    private int minRating;

    private String sort = "popular";

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 0)
    private BigDecimal minPrice;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 0)
    private BigDecimal maxPrice;

    /** Kiểm tra khoảng giá trước khi truy vấn sản phẩm. */
    @AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }
}
