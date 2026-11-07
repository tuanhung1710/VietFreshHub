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
    /** Chỉ lọc mức tồn trong chế độ tổng tồn theo SKU. */
    private String stock = "";
    /** Trạng thái lô: còn hạn, sắp/đã hết hạn hoặc hết hàng. */
    private String expiry = "";
    private Long variantId;
    private String view = "lots";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedTo;

    public boolean isSkuView() {
        return "sku".equals(view);
    }
}
