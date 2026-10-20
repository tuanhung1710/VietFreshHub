-- Read-only audit. Run in the same database as the application.
DECLARE @today date = CONVERT(date, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
;WITH active_categories AS (
    SELECT category_id FROM dbo.categories WHERE parent_category_id IS NULL AND status = 'ACTIVE'
    UNION ALL
    SELECT c.category_id FROM dbo.categories c
    JOIN active_categories a ON c.parent_category_id = a.category_id WHERE c.status = 'ACTIVE'
), stock AS (
    SELECT v.product_id, v.variant_id, v.status, v.price,
        COALESCE(SUM(CASE WHEN b.status = 'ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date >= @today)
            THEN CAST(b.quantity_on_hand AS bigint) - b.reserved_quantity ELSE 0 END), 0) available,
        MAX(CASE WHEN b.quantity_on_hand < b.reserved_quantity THEN 1 ELSE 0 END) invalid_stock
    FROM dbo.product_variants v LEFT JOIN dbo.inventory_batches b ON b.variant_id = v.variant_id
    GROUP BY v.product_id, v.variant_id, v.status, v.price
), variants AS (
    SELECT product_id,
        SUM(CASE WHEN status IN ('ACTIVE', 'OUT_OF_STOCK') THEN 1 ELSE 0 END) visible_skus,
        SUM(CASE WHEN status = 'ACTIVE' AND available > 0 THEN 1 ELSE 0 END) purchasable_skus,
        MAX(CASE WHEN status IN ('ACTIVE', 'OUT_OF_STOCK') AND (price < 10000 OR price <> FLOOR(price) OR price IS NULL) THEN 1 ELSE 0 END) invalid_price,
        MAX(invalid_stock) invalid_stock
    FROM stock GROUP BY product_id
), signals AS (
    SELECT p.product_id, p.name, p.status, p.approval_status, p.moderation_blocked, p.seller_hidden,
        s.status shop_status, s.availability_status, COALESCE(v.visible_skus, 0) visible_skus,
        COALESCE(v.purchasable_skus, 0) purchasable_skus,
        COALESCE(v.invalid_price, 0) invalid_price, COALESCE(v.invalid_stock, 0) invalid_stock,
        CASE WHEN EXISTS (SELECT 1 FROM dbo.product_categories pc JOIN active_categories c ON pc.category_id = c.category_id
            WHERE pc.product_id = p.product_id) THEN 1 ELSE 0 END category_visible
    FROM dbo.products p LEFT JOIN dbo.shops s ON p.shop_id = s.shop_id
    LEFT JOIN variants v ON p.product_id = v.product_id
), audited AS (
    SELECT *, CASE WHEN status = 'ACTIVE' AND approval_status = 'APPROVED' AND shop_status = 'ACTIVE'
        AND category_visible = 1 AND visible_skus > 0 THEN 1 ELSE 0 END catalog_visible,
        CONCAT(CASE WHEN status <> 'ACTIVE' THEN 'product_not_active; ' ELSE '' END,
               CASE WHEN approval_status <> 'APPROVED' THEN 'not_approved; ' ELSE '' END,
               CASE WHEN shop_status IS NULL OR shop_status <> 'ACTIVE' THEN 'shop_not_active; ' ELSE '' END,
               CASE WHEN category_visible = 0 THEN 'no_active_category_tree; ' ELSE '' END,
               CASE WHEN visible_skus = 0 THEN 'no_visible_variant; ' ELSE '' END) hidden_reasons
    FROM signals
)
SELECT *, SUM(catalog_visible) OVER () total_catalog_products,
    CEILING(SUM(catalog_visible) OVER () / 12.0) total_catalog_pages,
    CASE WHEN catalog_visible = 1 AND availability_status = 'OPEN' AND purchasable_skus > 0 THEN 1 ELSE 0 END catalog_purchasable
FROM audited ORDER BY product_id DESC OPTION (MAXRECURSION 100);
