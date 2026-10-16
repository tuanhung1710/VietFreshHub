package VietFreshHub.product.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Filters for the seller product table. Inventory keeps its own search contract. */
@Getter
@Setter
public class ProductCatalogFilter {
    private String q = "";
    private Long category;
    private String tab = "";
    private String stock = "";
    private String sort = "updated_desc";
    private String approval = "";
    private String eligibility = "";
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdTo;

    public boolean isAdvanced() {
        return createdFrom != null || createdTo != null || minPrice != null || maxPrice != null;
    }
}
