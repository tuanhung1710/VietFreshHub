package VietFreshHub.product.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Bộ lọc danh sách sản phẩm của người bán; màn hình kho dùng bộ lọc riêng. */
@Getter
@Setter
public class ProductCatalogFilter {
    private String q = "";
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
