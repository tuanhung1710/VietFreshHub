package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import org.springframework.data.domain.Page;

public interface CatalogService {
    boolean isVisible(CatalogProduct product);
    boolean isPurchasable(CatalogVariant variant);
    Page<CatalogProductResponse> listProducts(int page);
    Page<CatalogProductResponse> listProducts(int page, String keyword);
    CatalogProductResponse getProduct(Long variantId, Long productId, String slug);
}
