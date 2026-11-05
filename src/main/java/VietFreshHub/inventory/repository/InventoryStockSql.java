package VietFreshHub.inventory.repository;

/**
 * Cộng tồn theo SKU trước khi nối sang sản phẩm. Product và Inventory dùng cùng phép tính để tồn thực
 * tế, lượng giữ và khả dụng luôn khớp nhau. __scope__ dành cho điều kiện phạm vi do repository cung cấp.
 */
public final class InventoryStockSql {
    private InventoryStockSql() {}

    public static final String VARIANT_STOCK =
            """
        WITH variant_stock AS (
          SELECT v.product_id, v.variant_id, v.status, v.price,v.low_stock_threshold,v.sku,v.variant_name,v.unit,
            COALESCE(SUM(CAST(b.quantity_on_hand AS BIGINT)),0) AS physical,
            COALESCE(SUM(CAST(b.reserved_quantity AS BIGINT)),0) AS reserved,
            SUM(CASE WHEN b.quantity_on_hand>0 THEN CAST(1 AS BIGINT) ELSE 0 END) AS batches_in_stock,
            SUM(CASE WHEN b.expiry_date<:today THEN CAST(b.quantity_on_hand AS BIGINT) ELSE 0 END) AS expired_quantity,
            COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date>=:today)
              AND b.quantity_on_hand>b.reserved_quantity
              THEN CAST(b.quantity_on_hand AS BIGINT)-b.reserved_quantity ELSE 0 END),0) AS available
          FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id
          LEFT JOIN dbo.inventory_batches b ON b.variant_id=v.variant_id
          WHERE p.shop_id=:shop __scope__
          GROUP BY v.product_id,v.variant_id,v.status,v.price,v.low_stock_threshold,v.sku,v.variant_name,v.unit
        )
        """;
}
