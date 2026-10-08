package VietFreshHub.Product.service;

import VietFreshHub.Product.dto.CatalogProductResponse;
import VietFreshHub.Product.entity.Product;
import VietFreshHub.Product.entity.ProductVariant;
import org.springframework.data.domain.Page;

public interface CatalogService {
    boolean isVisible(Product product);
    boolean isPurchasable(ProductVariant variant);
    Page<CatalogProductResponse> listProducts(int page);
    Page<CatalogProductResponse> listProducts(int page, String keyword);
    CatalogProductResponse getProduct(Long variantId, Long productId, String slug);
}
