package VietFreshHub.inventory.dto;

/** Đếm theo phân loại hoặc lô, tránh cộng số lượng hàng khác đơn vị bán. */
public record InventoryAttentionSummary(long lowVariants, long nearBatches, long expiredBatches) {
    /** Cho biết cửa hàng có phân loại sắp hết hàng hoặc lô cận hạn, hết hạn cần chú ý. */
    public boolean hasAlerts() { return lowVariants + nearBatches + expiredBatches > 0; }
}
