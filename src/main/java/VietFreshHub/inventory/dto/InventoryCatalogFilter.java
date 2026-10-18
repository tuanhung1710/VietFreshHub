package VietFreshHub.inventory.dto;

import lombok.Getter;
import lombok.Setter;

/** Separate, combinable dimensions. Pagination counts products, matching children are SKUs/lots. */
@Getter @Setter
public class InventoryCatalogFilter {
    private String q = "";
    private Long category;
    private String stock = "";
    private String expiry = "";
    private Long variantId;
    /** Includes soft-deleted products/SKUs, even with no stock. False shows current SKUs only. */
    private boolean archived;
}
