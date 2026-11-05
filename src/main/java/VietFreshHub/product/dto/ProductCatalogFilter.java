package VietFreshHub.product.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Bộ lọc danh sách sản phẩm của người bán; màn hình kho dùng bộ lọc riêng. */
@Getter
@Setter
public class ProductCatalogFilter {
    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
    private String q = "";

    @Positive(message = "Danh mục không hợp lệ.")
    private Long category;

    private String tab = "";
    private String stock = "";
    private String sort = "updated_desc";
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdTo;
}
