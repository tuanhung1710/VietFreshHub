package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.entity.InventoryTransaction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JournalResponse {

    private InventoryTransaction event;
    private String productName;
    private String sku;
    private String orderCode;
    private String actorName;
    private Long quantityOnHandAfter;
    private Long reservedQuantityAfter;

    public JournalResponse(
            InventoryTransaction event, String productName, String sku, String orderCode, String actorName) {
        this(event, productName, sku, orderCode, actorName, null, null);
    }

    public JournalResponse(InventoryTransaction event, String productName, String sku) {
        this(event, productName, sku, null, "Người dùng");
    }
    public String getBatchCode() {
        return VietFreshHub.common.BusinessCodes.format("LOT", event.getBatchId());
    }

    public boolean isStockBalanceKnown() {
        return quantityOnHandAfter != null && reservedQuantityAfter != null
                && quantityOnHandAfter >= 0 && reservedQuantityAfter >= 0
                && reservedQuantityAfter <= quantityOnHandAfter;
    }

    public String getQuantityDescription() {
        long quantity = Math.abs((long) event.getQuantity());
        return switch (event.getTransactionType()) {
            case RESTOCK -> "Nhập " + quantity + " sản phẩm vào lô.";
            case RESERVE -> "Giữ " + quantity + " sản phẩm cho đơn.";
            case RELEASE -> "Giải phóng " + quantity + " sản phẩm đã giữ.";
            case SALE -> "Xuất bán " + quantity + " sản phẩm đã giữ cho đơn.";
            case RETURN -> "Nhập hoàn " + quantity + " sản phẩm vào lô.";
            case ADJUSTMENT -> (event.getQuantity() > 0 ? "Tăng tồn kho " : "Giảm tồn kho ")
                    + quantity + " sản phẩm.";
            case EXPIRED -> "Hủy " + quantity + " sản phẩm hết hạn.";
            case DAMAGED -> "Hủy " + quantity + " sản phẩm hư hỏng.";
        };
    }

    public String getReferenceLabel() {
        if (event.getReferenceId() == null || event.getReferenceType() == null) return "";
        return switch (event.getReferenceType()) {
            case "BATCH" -> "Lô " + VietFreshHub.common.BusinessCodes.format("LOT", event.getReferenceId());
            case "ORDER_ITEM" -> orderCode == null ? "Dòng sản phẩm trong đơn" : "Đơn " + orderCode;
            default -> event.getReferenceType();
        };
    }
}
