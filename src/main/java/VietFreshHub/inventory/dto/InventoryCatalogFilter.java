package VietFreshHub.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bộ lọc dùng chung cho danh sách lô và tổng tồn theo SKU. */
@Getter
@Setter
public class InventoryCatalogFilter {
    private String q = "";
    private String stock = "";
    private String expiry = "";
    private Long variantId;
    private String view = "lots";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedTo;

    /** Gồm cả sản phẩm, SKU đã xóa dù đã hết tồn; tắt thì chỉ hiện SKU đang kinh doanh. */
    private boolean archived;

    public boolean isSkuView() {
        return "sku".equals(view);
    }
}
