package VietFreshHub.product.dto;

import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.product.entity.ProductVariant;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class VariantResponse {

    private ProductVariant variant;
    private String productName;
    private long physical;
    private long reserved;
    private long available;
    private long unavailable;
    private List<BatchResponse> batches;
    private List<BatchResponse> matchedBatches;
    private BigDecimal offerPrice;
    private long offerQuantity;
    private boolean lowStock;
    private int batchCount;
    private boolean expired;
    private boolean nearExpiry;

    public List<BatchResponse> getInventoryBatches() {
        return matchedBatches == null ? batches : matchedBatches;
    }

    public BigDecimal getReferencePrice() {
        BigDecimal reference = variant.getCompareAtPrice();
        BigDecimal price = variant.getPrice();
        return reference != null && price != null && reference.stripTrailingZeros().scale() <= 0
                && reference.signum() >= 0 && reference.compareTo(price) >= 0 ? reference : null;
    }
}
