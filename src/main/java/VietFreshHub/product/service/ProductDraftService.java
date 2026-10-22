package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductRequest;

/** Builds unsaved form data. Persistence and publication still belong to ProductService. */
public interface ProductDraftService {
    void addVariant(ProductRequest form);

    void removeVariant(ProductRequest form, int index);

    void useProductImage(ProductRequest form, int index);
}
