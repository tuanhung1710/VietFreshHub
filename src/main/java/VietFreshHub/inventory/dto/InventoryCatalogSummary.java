package VietFreshHub.inventory.dto;

public record InventoryCatalogSummary(
        long skuCount,
        long totalBatches,
        long batchesInStock,
        long nearBatches,
        long expiredBatches,
        long totalExpiredBatches) {}
