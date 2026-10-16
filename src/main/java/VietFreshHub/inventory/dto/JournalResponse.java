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

    public JournalResponse(InventoryTransaction event, String productName, String sku) {
        this(event, productName, sku, null, "Người dùng");
    }
    public String getBatchCode() {
        return VietFreshHub.common.BusinessCodes.format("LOT", event.getBatchId());
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
