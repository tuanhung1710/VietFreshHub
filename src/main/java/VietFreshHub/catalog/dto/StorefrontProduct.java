package VietFreshHub.catalog.dto;

import java.math.BigDecimal;
import VietFreshHub.catalog.service.FruitSymbols;

/** Một thẻ sản phẩm luôn gắn với SKU mua được và đúng giá đang hiển thị. */
public record StorefrontProduct(Long id, String name, String emoji, Long categoryId, String category,
                                Long shopId, String shopName, String place, BigDecimal price,
                                BigDecimal rating, long sales, Long variantId, String variantName, int availableStock) {
    public static StorefrontProduct from(CatalogProductResponse product) {
        var variant = product.getSelectedVariant();
        return new StorefrontProduct(product.getProductId(), product.getName(), FruitSymbols.forName(product.getName()),
                product.getCategoryId(), product.getCategoryName(), product.getShopId(), product.getShopName(), product.getShopPlace(),
                variant.getPrice(), product.getRating() == null ? BigDecimal.ZERO : product.getRating(), product.getSales(),
                variant.getVariantId(), variant.getVariantName(), variant.getAvailableStock());
    }
}
