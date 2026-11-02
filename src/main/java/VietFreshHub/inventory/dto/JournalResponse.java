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

    public String getBatchCode() {
        return VietFreshHub.common.BusinessCodes.format("LOT", event.getBatchId());
    }

    public String getDisplayProductName() {
        return hasText(productName) ? productName : "Sản phẩm không còn dữ liệu";
    }

    public String getDisplaySku() {
        return hasText(sku) ? sku : "SKU không còn dữ liệu";
    }

    public String getDisplayActorName() {
        return hasText(actorName) ? actorName : "Người dùng không còn dữ liệu";
    }

    public String getTransactionTypeLabel() {
        if (event.getTransactionType() == null) return "Loại biến động không xác định";
        return switch (event.getTransactionType()) {
            case RESTOCK -> "Nhập kho";
            case RESERVE -> "Giữ hàng cho đơn";
            case RELEASE -> "Giải phóng hàng giữ";
            case SALE -> "Xuất bán";
            case RETURN -> "Nhập hàng hoàn";
            case ADJUSTMENT -> "Điều chỉnh tồn kho";
            case EXPIRED -> "Hủy hàng hết hạn";
            case DAMAGED -> "Hàng hư hỏng";
        };
    }

    public String getQuantityLabel() {
        Integer quantity = event.getQuantity();
        if (quantity == null) return "Không có số liệu";
        return quantity > 0 ? "+" + quantity : quantity.toString();
    }

    public String getQuantityCssClass() {
        Integer quantity = event.getQuantity();
        if (quantity == null) return "text-body-secondary";
        return quantity > 0 ? "text-success" : "text-danger";
    }

    public String getQuantityDescription() {
        if (event.getTransactionType() == null) return "Không có loại biến động để diễn giải số lượng.";
        return event.getTransactionType() == VietFreshHub.inventory.enums.StockTransactionType.RESERVE
                || event.getTransactionType() == VietFreshHub.inventory.enums.StockTransactionType.RELEASE
                ? "Thay đổi số lượng khả dụng do giữ hoặc giải phóng hàng cho đơn. Tổng tồn kho không thay đổi."
                : "Số lượng tồn kho thay đổi trong giao dịch này.";
    }

    public String getReferenceLabel() {
        if (event.getReferenceType() == null) return "";
        if ("ORDER_ITEM".equals(event.getReferenceType())) {
            return orderCode == null ? "Đơn hàng không còn dữ liệu" : "Đơn " + orderCode;
        }
        if (event.getReferenceId() == null) return "";
        return switch (event.getReferenceType()) {
            case "BATCH" -> "Lô " + VietFreshHub.common.BusinessCodes.format("LOT", event.getReferenceId());
            default -> event.getReferenceType();
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
