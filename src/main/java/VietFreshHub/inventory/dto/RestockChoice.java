package VietFreshHub.inventory.dto;

/** Sản phẩm và SKU để chọn khi nhập lô mới, không phụ thuộc các lô đã nhập. */
public record RestockChoice(
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String unit) {}
