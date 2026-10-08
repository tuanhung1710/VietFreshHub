package VietFreshHub.Product.dto;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
@Getter @Builder
public class CatalogProductResponse {
    private Long productId;
    private String name, slug, description, imageUrl, shopName;
    private List<VariantResponse> variants;
    private VariantResponse selectedVariant;
    @Getter @Builder
    public static class VariantResponse {
        private Long variantId;
        private String variantName, sku, status;
        private BigDecimal price;
        private int availableStock;
        private boolean purchasable;
    }
}
