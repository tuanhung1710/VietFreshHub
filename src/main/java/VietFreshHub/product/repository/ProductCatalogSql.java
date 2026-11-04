package VietFreshHub.product.repository;

import VietFreshHub.product.dto.ProductCatalogFilter;
import java.time.LocalDateTime;

/** Các mảnh SQL dùng chung giữa truy vấn đếm, lấy trang và chọn SKU khớp bộ lọc. */
final class ProductCatalogSql {
    private ProductCatalogSql() {}

    // Bước 1: cộng tồn của từng SKU trước khi ghép với sản phẩm để tránh nhân đôi số lượng theo lô.
    // Hàng khả dụng chỉ lấy từ lô đang hoạt động, còn hạn và còn phần chưa giữ cho đơn.
    static final String VARIANT_STOCK = """
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

    // Bước 2: gom tín hiệu của các SKU lên sản phẩm, bổ sung điều kiện shop và danh mục.
    // Bước 3: phân trạng thái theo ưu tiên: vi phạm -> chủ shop ẩn -> không đủ điều kiện -> còn/hết hàng.
    // Cùng kết quả này được dùng cho danh sách, tab thống kê và kiểm tra thao tác để không lệch nhau.
    static final String CLASSIFICATION = VARIANT_STOCK.replace("__scope__", "AND p.status<>'DELETED'") + """
        , variant_signals AS (
          SELECT product_id,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') AND price>=10000 AND price=FLOOR(price) THEN 1 ELSE 0 END) AS has_variant,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') AND price>=10000 AND price=FLOOR(price) AND available>0 THEN 1 ELSE 0 END) AS has_stock,
            MAX(CASE WHEN status='ACTIVE' AND price>=10000 AND price=FLOOR(price) AND available>0 THEN 1 ELSE 0 END) AS can_purchase,
            MAX(CASE WHEN status<>'DELETED' AND available>0 THEN 1 ELSE 0 END) AS has_available,
            MAX(CASE WHEN status<>'DELETED' AND available>0 AND available<=COALESCE(low_stock_threshold,0) THEN 1 ELSE 0 END) AS has_low,
            MAX(CASE WHEN status IN ('ACTIVE','OUT_OF_STOCK') THEN 1 ELSE 0 END) AS has_open_variant
          FROM variant_stock GROUP BY product_id
        ), product_signals AS (
          SELECT p.product_id,p.status,p.seller_hidden,p.moderation_blocked,p.moderation_reason,
            CASE WHEN s.status='ACTIVE' AND s.availability_status='OPEN' THEN 1 ELSE 0 END AS shop_open,
            CASE WHEN EXISTS (SELECT 1 FROM dbo.product_categories pc
              WHERE pc.product_id=p.product_id AND pc.category_id IN (:categories)) THEN 1 ELSE 0 END AS has_category,
            COALESCE(v.has_variant,0) AS has_variant,COALESCE(v.has_stock,0) AS has_stock,
            COALESCE(v.can_purchase,0) AS can_purchase,
            COALESCE(v.has_available,0) AS has_available,COALESCE(v.has_low,0) AS has_low,
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

    static String managementWhere(ProductCatalogFilter filters, LocalDateTime from, LocalDateTime to) {
        return managementBaseWhere(filters, from, to)
                + managementStockCriteria(filters)
                + managementPriceCriteria(filters)
                + managementSearchCriteria(filters.getQ());
    }

    private static String managementBaseWhere(ProductCatalogFilter filters, LocalDateTime from, LocalDateTime to) {
        Long category = filters.getCategory();
        String tab = filters.getTab();
        String where = " FROM dbo.products p JOIN classified c ON c.product_id=p.product_id JOIN product_signals ps ON ps.product_id=p.product_id WHERE 1=1";
        if (!tab.isEmpty()) {
            where += " AND c.management_state=:tab";
        }
        if (category != null) {
            where += " AND EXISTS (SELECT 1 FROM dbo.product_categories pc WHERE pc.product_id=p.product_id AND pc.category_id=:category)";
        }
        if (from != null) {
            where += " AND p.created_at>=:from";
        }
        if (to != null) {
            where += " AND p.created_at<:to";
        }

        return where;
    }

    private static String managementStockCriteria(ProductCatalogFilter filters) {
        return switch (filters.getStock()) {
            case "available" -> " AND ps.has_available=1";
            case "empty" -> " AND ps.has_available=0";
            case "low" -> " AND ps.has_low=1";
            case "expiry" -> " " + """
                AND EXISTS (SELECT 1 FROM dbo.product_variants ev JOIN dbo.inventory_batches eb ON eb.variant_id=ev.variant_id
                  WHERE ev.product_id=p.product_id AND ev.status<>'DELETED' AND eb.quantity_on_hand>0
                    AND eb.expiry_date<=DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE)))
                """;
            default -> "";
        };

    }

    /** Hai mốc giá phải khớp cùng một SKU. */
    private static String managementPriceCriteria(ProductCatalogFilter filters) {
        String where = "";
        if (filters.getMinPrice() != null || filters.getMaxPrice() != null) {
            where += " AND EXISTS (SELECT 1 FROM dbo.product_variants pv WHERE pv.product_id=p.product_id AND pv.status<>'DELETED'";
            if (filters.getMinPrice() != null) {
                where += " AND pv.price>=:minPrice";
            }
            if (filters.getMaxPrice() != null) {
                where += " AND pv.price<=:maxPrice";
            }
            where += ")";
        }

        return where;
    }

    private static String managementSearchCriteria(String text) {
        if (text.isEmpty()) {
            return "";
        }
        return " " + """
            AND ((:productCode<0 AND :batchCode<0 AND (LOWER(p.name) LIKE :text OR LOWER(p.slug) LIKE :text
              OR EXISTS (SELECT 1 FROM dbo.product_variants v WHERE v.product_id=p.product_id
                AND (LOWER(v.sku) LIKE :text OR LOWER(v.variant_name) LIKE :text))))
              OR p.product_id=:productCode
              OR EXISTS (SELECT 1 FROM dbo.inventory_batches b JOIN dbo.product_variants bv ON bv.variant_id=b.variant_id
                WHERE bv.product_id=p.product_id AND b.batch_id=:batchCode))
            """;

    }
}
