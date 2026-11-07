package VietFreshHub.catalog.repository;

/** Giá bán SKU và tổng lượng khả dụng từ các lô còn hạn, đang hoạt động. */
public final class CatalogOfferSql {
    private CatalogOfferSql() {}

    public static final String CTE =
            """
        WITH variant_offers AS (
            SELECT v.variant_id, v.product_id, v.status, v.price,
                   COALESCE(SUM(CAST(b.quantity_on_hand AS BIGINT) - b.reserved_quantity), 0) AS stock
            FROM dbo.product_variants v
            LEFT JOIN dbo.inventory_batches b ON b.variant_id = v.variant_id
                AND b.status = 'ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date >= :today)
                AND b.quantity_on_hand > b.reserved_quantity
            GROUP BY v.variant_id, v.product_id, v.status, v.price
        )
        """;
}
