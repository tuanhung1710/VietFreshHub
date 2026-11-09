package VietFreshHub.product.repository;

import static VietFreshHub.inventory.repository.InventoryStockSql.VARIANT_STOCK;

import VietFreshHub.product.dto.ProductCatalogFilter;

/** Các mảnh SQL dùng chung giữa truy vấn đếm và lấy trang sản phẩm. */
final class ProductCatalogSql {
    private ProductCatalogSql() {}

    // Bước 2: gom tín hiệu của các SKU lên sản phẩm, bổ sung điều kiện shop và danh mục.
    // Bước 3: phân trạng thái theo ưu tiên: vi phạm -> chủ shop ẩn -> không đủ điều kiện -> còn/hết
    // hàng.
    // Cùng kết quả này được dùng cho danh sách, tab thống kê và kiểm tra thao tác để không lệch
    // nhau.
    static final String CLASSIFICATION =
            VARIANT_STOCK.replace("__scope__", "AND p.status<>'DELETED'")
                    + """
        , variant_signals AS (
          SELECT product_id,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') AND price>=10000 AND price=FLOOR(price) THEN 1 ELSE 0 END) AS has_variant,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') AND price>=10000 AND price=FLOOR(price) AND available>0 THEN 1 ELSE 0 END) AS has_stock,
            MAX(CASE WHEN status='ACTIVE' AND price>=10000 AND price=FLOOR(price) AND available>0 THEN 1 ELSE 0 END) AS can_purchase,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') THEN 1 ELSE 0 END) AS has_open_variant
          FROM variant_stock GROUP BY product_id
        ), product_signals AS (
          SELECT p.product_id,p.status,p.seller_hidden,p.moderation_blocked,p.moderation_reason,
            CASE WHEN s.status='ACTIVE' AND s.availability_status='OPEN' THEN 1 ELSE 0 END AS shop_open,
            CASE WHEN EXISTS (SELECT 1 FROM dbo.product_categories pc
              WHERE pc.product_id=p.product_id AND pc.category_id IN (:categories)) THEN 1 ELSE 0 END AS has_category,
            COALESCE(v.has_variant,0) AS has_variant,COALESCE(v.has_stock,0) AS has_stock,
            COALESCE(v.can_purchase,0) AS can_purchase,
            COALESCE(v.has_open_variant,0) AS has_open_variant
          FROM dbo.products p LEFT JOIN dbo.shops s ON s.shop_id=p.shop_id
          LEFT JOIN variant_signals v ON v.product_id=p.product_id
          WHERE p.shop_id=:shop AND p.status<>'DELETED'
        ), classified AS (
          SELECT product_id,
            CASE WHEN moderation_blocked=1 THEN 'violation'
              WHEN status='INACTIVE' AND seller_hidden=1 THEN 'hidden'
              WHEN status<>'ACTIVE' OR shop_open=0 OR has_category=0 OR has_variant=0 THEN 'unavailable'
              WHEN can_purchase=1 THEN 'active'
              WHEN has_stock=0 THEN 'out-of-stock'
              ELSE 'unavailable' END AS management_state,
            CASE WHEN moderation_blocked=1 THEN COALESCE(moderation_reason,N'Admin đã khóa sản phẩm.')
              WHEN status='INACTIVE' AND seller_hidden=1 THEN N'Chủ shop đã ẩn sản phẩm.'
              WHEN status<>'ACTIVE' THEN N'Sản phẩm chưa được bật bán; dữ liệu cũ chưa xác định được lý do ẩn.'
              WHEN shop_open=0 THEN N'Shop hiện không nhận đơn hoặc chưa có thông tin trạng thái shop.'
              WHEN has_category=0 THEN N'Sản phẩm chưa có danh mục đang hoạt động.'
              WHEN has_variant=0 THEN N'Chưa có phân loại kinh doanh với giá bán hợp lệ.'
              WHEN can_purchase=1 THEN ''
              WHEN has_stock=0 THEN N'Không còn tồn kho khả dụng ở các phân loại kinh doanh.'
              ELSE N'Phân loại còn hàng chưa được bật bán.' END AS state_reason
          FROM product_signals
        )
        """;

    // Ham dung de ghep cac dieu kien loc,
    static String managementWhere(ProductCatalogFilter filters) {
        return managementBaseWhere(filters) + managementSearchCriteria(filters.getQ());
    }

    // Tao phan truy van co ban
    private static String managementBaseWhere(ProductCatalogFilter filters) {
        Long category = filters.getCategory();
        String tab = filters.getTab();
        String where =
                " FROM dbo.products p JOIN classified c ON c.product_id=p.product_id WHERE 1=1";
        if (!tab.isEmpty()) {
            where += " AND c.management_state=:tab";
        }
        if (category != null) {
            where +=
                    " AND EXISTS (SELECT 1 FROM dbo.product_categories pc WHERE pc.product_id=p.product_id AND pc.category_id=:category)";
        }

        return where;
    }

    // Loc theo tu khoa hoac ma lo
    private static String managementSearchCriteria(String text) {
        if (text.isEmpty()) {
            return "";
        }
        return " "
                + """
            AND ((:productCode<0 AND :batchCode<0 AND (LOWER(p.name) LIKE :text OR LOWER(p.slug) LIKE :text
              OR EXISTS (SELECT 1 FROM dbo.product_variants v WHERE v.product_id=p.product_id
                AND (LOWER(v.sku) LIKE :text OR LOWER(v.variant_name) LIKE :text))))
              OR p.product_id=:productCode
              OR EXISTS (SELECT 1 FROM dbo.inventory_batches b JOIN dbo.product_variants bv ON bv.variant_id=b.variant_id
                WHERE bv.product_id=p.product_id AND b.batch_id=:batchCode))
            """;
    }
}
