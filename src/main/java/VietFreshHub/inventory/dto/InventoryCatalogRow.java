package VietFreshHub.inventory.dto;

import VietFreshHub.common.BusinessCodes;

import java.time.LocalDate;

/**
 * Một dòng lô hoặc một dòng tổng hợp SKU. Ở chế độ SKU, batchId/ngày nhập/hạn dùng là null;
 * physical gồm cả reserved, còn available đã loại hàng giữ, lô bị khóa và hàng hết hạn.
 */
public record InventoryCatalogRow(
        Long batchId,
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String unit,
        LocalDate receivedDate,
        LocalDate expiryDate,
        long physical,
        long reserved,
        long available,
        long batchesInStock,
        long expiredQuantity,
        boolean expired,
        boolean nearExpiry,
        boolean blocked,
        boolean archived,
        boolean lowStock) {
    public String batchCode() {
        return BusinessCodes.format("LOT", batchId);
    }
}
