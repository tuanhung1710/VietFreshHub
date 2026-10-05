package VietFreshHub.Product.dto;
import java.util.List;
import VietFreshHub.Product.entity.Product;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.enums.VariantStatus;
import VietFreshHub.Inventory.dto.BatchResponse;
import java.math.BigDecimal;
public record ProductResponse(Product product,String imageUrl,String categoryName,List<VariantResponse> variants,
        List<String> categoryNames,String approvedByName,boolean published) {
    public List<VariantResponse> visibleVariants() {
        return variants.stream().filter(v->v.variant().getStatus()!=VariantStatus.DELETED).toList();
    }
    public List<VariantResponse> inventoryVariants() {
        return variants.stream().filter(v->v.variant().getStatus()!=VariantStatus.DELETED||v.physical()>0||v.reserved()>0).toList();
    }
    public int variantCount() { return visibleVariants().size(); }
    public int batchCount() { return inventoryVariants().stream().mapToInt(VariantResponse::batchCount).sum(); }
    public boolean hasExpired() { return inventoryVariants().stream().anyMatch(VariantResponse::hasExpired); }
    public boolean hasNearExpiry() { return inventoryVariants().stream().anyMatch(VariantResponse::hasNearExpiry); }
    public BigDecimal minPrice() { return visibleVariants().stream().map(v->v.variant().getPrice()).filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null); }
    public BigDecimal maxPrice() { return visibleVariants().stream().map(v->v.variant().getPrice()).filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null); }
    public BigDecimal minComparePrice() { return visibleVariants().stream().map(v->v.variant().getCompareAtPrice()).filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null); }
    public BigDecimal maxComparePrice() { return visibleVariants().stream().map(v->v.variant().getCompareAtPrice()).filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null); }
    public record VariantResponse(ProductVariant variant,long physical,long reserved,long available,long unavailable,
       List<BatchResponse> batches, BigDecimal offerPrice,long offerQuantity) {
        public boolean lowStock() { return available<= (variant.getLowStockThreshold()==null?0:variant.getLowStockThreshold()); }
        public int batchCount() { return batches.size(); }
        public boolean hasExpired() { return batches.stream().anyMatch(b->b.expired()&&b.batch().getQuantityOnHand()>0); }
        public boolean hasNearExpiry() { return batches.stream().anyMatch(b->b.nearExpiry()&&b.batch().getQuantityOnHand()>0); }
    }
}
