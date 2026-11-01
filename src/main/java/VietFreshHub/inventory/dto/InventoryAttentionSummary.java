package VietFreshHub.inventory.dto;

/** Counts use SKU/lot units, never add quantities with different sale units. */
public record InventoryAttentionSummary(long lowVariants, long nearBatches, long expiredBatches,
                                        long inventoryBatches) {
    public boolean hasAlerts() { return lowVariants + nearBatches + expiredBatches > 0; }
    public boolean hasInventoryData() { return inventoryBatches > 0; }
}
