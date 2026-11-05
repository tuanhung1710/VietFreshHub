package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;

import org.springframework.data.domain.Page;

import java.util.List;

public interface CatalogService {
    boolean isVisible(CatalogProduct product);

    boolean isPurchasable(CatalogVariant variant);

    Page<CatalogProductResponse> listProducts(int page);

    Page<CatalogProductResponse> listProducts(CatalogBrowseFilter filter);

    List<CatalogCategory> categories();

    CatalogProductResponse getProduct(Long variantId, Long productId, String slug);
}
