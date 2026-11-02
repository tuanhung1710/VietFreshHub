package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chỉ trả thông tin được phép hiển thị, không trả thông tin lô hoặc đơn hàng. */
@RestController
@RequiredArgsConstructor
public class ProductAvailabilityController {
    private final CatalogService catalogService;

    public record VariantAvailability(Long variantId, String variantName, BigDecimal price,
                                      String imageUrl, int availableStock, boolean purchasable,
                                      String availabilityMessage) {
    }

    public record ProductAvailability(Long productId, List<VariantAvailability> variants) {
    }

    @GetMapping("/api/catalog/products/{productId}/availability")
    public ResponseEntity<ProductAvailability> availability(@PathVariable Long productId) {
        CatalogProductResponse product = catalogService.getProduct(null, productId, null);
        List<VariantAvailability> variants = new ArrayList<>();
        for (CatalogProductResponse.VariantResponse variant : product.getVariants()) {
            variants.add(new VariantAvailability(variant.getVariantId(), variant.getVariantName(),
                    variant.getPrice(), variant.getImageUrl(), variant.getAvailableStock(),
                    variant.isPurchasable(), variant.getAvailabilityMessage()));
        }
        ProductAvailability availability = new ProductAvailability(product.getProductId(), List.copyOf(variants));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(availability);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> unavailable(ResourceNotFoundException exception) {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "PRODUCT_UNAVAILABLE", "message", exception.getMessage()));
    }
}
