package VietFreshHub.catalog.repository;

/** Giá thấp nhất và số lượng có thể mua tại đúng mức giá đó; ngày do InventoryRules cung cấp. */
public final class CatalogOfferSql {
    private CatalogOfferSql() {}

    public static final String CTE =
            """
        WITH priced_batches AS (
            SELECT b.batch_id, b.variant_id,
                   CAST(b.quantity_on_hand AS BIGINT) - b.reserved_quantity AS quantity,
                   COALESCE(MIN(m.price), v.price) AS price
            FROM dbo.inventory_batches b
            JOIN dbo.product_variants v ON v.variant_id = b.variant_id
            LEFT JOIN dbo.batch_markdowns m ON m.batch_id = b.batch_id AND m.status = 'ACTIVE'
                AND m.start_date <= :today AND m.end_date >= :today
                AND b.expiry_date <= :nearDate AND m.price < v.price
            WHERE b.status = 'ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date >= :today)
                AND b.quantity_on_hand > b.reserved_quantity
            GROUP BY b.batch_id, b.variant_id, b.quantity_on_hand, b.reserved_quantity, v.price
        ), variant_prices AS (
            SELECT v.variant_id, v.product_id, v.status, v.price AS base_price,
                   COALESCE(MIN(b.price), v.price) AS price
            FROM dbo.product_variants v LEFT JOIN priced_batches b ON b.variant_id = v.variant_id
            GROUP BY v.variant_id, v.product_id, v.status, v.price
        ), variant_offers AS (
            SELECT v.variant_id, v.product_id, v.status, v.base_price, v.price,
                   COALESCE(SUM(b.quantity), 0) AS stock
            FROM variant_prices v LEFT JOIN priced_batches b ON b.variant_id = v.variant_id AND b.price = v.price
            GROUP BY v.variant_id, v.product_id, v.status, v.base_price, v.price
        )
        """;
}
