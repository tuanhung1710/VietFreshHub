package VietFreshHub.catalog.dto;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
@Getter @Builder
public class CatalogProductResponse {
    private Long productId;
    private Long shopId;
    private String name, slug, description, imageUrl, shopName;
    private String shopPlace, categoryName;
    private Long categoryId;
    private BigDecimal rating;
    private long sales;
    private List<VariantResponse> variants;
    private VariantResponse selectedVariant;
    @Getter @Builder
    public static class VariantResponse {
        private Long variantId;
        private String variantName, sku, status;
        private BigDecimal price;
        private BigDecimal basePrice;
        private int availableStock;
        private boolean purchasable;
    }
}
