package VietFreshHub.inventory.dto;

import lombok.Getter;
import lombok.Setter;

/** Các điều kiện kho có thể kết hợp; phân trang theo sản phẩm, lọc chi tiết theo SKU và lô. */
@Getter @Setter
public class InventoryCatalogFilter {
    private String q = "";
    private Long category;
    private String stock = "";
    private String expiry = "";
    private Long variantId;
    /** Gồm cả sản phẩm, SKU đã xóa dù đã hết tồn; tắt thì chỉ hiện SKU đang kinh doanh. */
    private boolean archived;

    public boolean isAdvanced() {
        return category != null || (stock != null && !stock.isBlank())
                || (expiry != null && !expiry.isBlank()) || archived;
    }
}
