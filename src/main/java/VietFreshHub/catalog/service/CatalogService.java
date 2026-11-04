package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.entity.CatalogCategory;
import java.util.List;
import org.springframework.data.domain.Page;

public interface CatalogService {
    boolean isVisible(CatalogProduct product);
    boolean isPurchasable(CatalogVariant variant);
    Page<CatalogProductResponse> listProducts(int page);
    Page<CatalogProductResponse> listProducts(CatalogBrowseFilter filter);
    List<CatalogCategory> categories();
    CatalogProductResponse getProduct(Long variantId, Long productId, String slug);
}
