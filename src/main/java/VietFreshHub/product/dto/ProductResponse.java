package VietFreshHub.product.dto;

import VietFreshHub.product.entity.Product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductResponse {

    private Product product;
    private String imageUrl;
    private String categoryName;
    private List<VariantResponse> variants;
    private List<String> categoryNames;
    private String approvedByName;
    private boolean published;
    private ProductManagementState managementState;
    private List<VariantResponse> visibleVariants;
    private List<VariantResponse> inventoryVariants;
    private int inventoryVariantTotal;
    private int variantCount;
    private int batchCount;
    private boolean expired;
    private boolean nearExpiry;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minComparePrice;
    private BigDecimal maxComparePrice;

    public boolean isCanDelete() {
        return product.getStatus() != VietFreshHub.product.enums.ProductStatus.DELETED
                && variants != null && variants.stream().noneMatch(v -> v.getReserved() > 0);
    }

    public long getAvailableVariantCount() {
        return visibleVariants == null ? 0 : visibleVariants.stream().filter(v -> v.getAvailable() > 0).count();
    }

    public long getInventoryPhysical() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getPhysical).sum();
    }

    public long getInventoryReserved() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getReserved).sum();
    }

    public long getInventoryAvailable() {
        return inventoryVariants == null ? 0 : inventoryVariants.stream().mapToLong(VariantResponse::getAvailable).sum();
    }

    public String getPriceUnit() {
        if (visibleVariants == null || visibleVariants.isEmpty()) return "";
        List<String> units = visibleVariants.stream().map(v -> v.getVariant().getUnit()).distinct().toList();
        return units.size() == 1 ? units.getFirst() : "";
    }

    public java.time.LocalDateTime getCatalogUpdatedAt() {
        var date = product.getUpdatedAt() == null ? product.getCreatedAt() : product.getUpdatedAt();
        return date == null ? null : date.plusHours(7);
    }
}
