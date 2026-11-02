package VietFreshHub.catalog.dto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
@Getter @Builder
public class CatalogProductResponse {
    private Long productId, shopId;
    private String name, slug, description, imageUrl, shopName;
    private String origin;
    private String shopPlace, categoryName;
    private Long categoryId;
    private BigDecimal rating;
    private long sales;
    private List<VariantResponse> variants;
    private VariantResponse selectedVariant;
    @Getter @Builder
    public static class VariantResponse {
        private Long variantId;
        private String variantName, sku, status, imageUrl;
        private BigDecimal price;
        private BigDecimal basePrice;
        private BigDecimal compareAtPrice;
        private String availabilityMessage;
        private int availableStock;
        private boolean purchasable;

        /** Làm tròn xuống để không hiển thị mức giảm cao hơn thực tế. */
        public Integer getDiscountPercent() {
            if (price == null || price.signum() < 0 || compareAtPrice == null
                    || compareAtPrice.signum() <= 0 || compareAtPrice.compareTo(price) <= 0) {
                return null;
            }
            return compareAtPrice.subtract(price).multiply(BigDecimal.valueOf(100))
                    .divide(compareAtPrice, 0, RoundingMode.DOWN).intValueExact();
        }
    }
}
