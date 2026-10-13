package VietFreshHub.product.service;

import VietFreshHub.product.dto.CatalogProductResponse;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import org.springframework.data.domain.Page;

public interface CatalogService {
    boolean isVisible(Product product);
    boolean isPurchasable(ProductVariant variant);
    Page<CatalogProductResponse> listProducts(int page);
    Page<CatalogProductResponse> listProducts(int page, String keyword);
    CatalogProductResponse getProduct(Long variantId, Long productId, String slug);
}
