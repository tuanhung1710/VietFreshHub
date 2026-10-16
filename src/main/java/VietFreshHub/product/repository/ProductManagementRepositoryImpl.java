package VietFreshHub.product.repository;

import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.entity.Product;
import VietFreshHub.common.BusinessCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One classification expression shared by search, counters, display and order eligibility.
 * Stock is aggregated per SKU before it reaches a product, avoiding duplicate product counts.
 * The final aggregate operates on columns, not subqueries inside SUM (SQL Server limitation).
 */
public class ProductManagementRepositoryImpl implements ProductManagementRepository {
    @PersistenceContext
    private EntityManager entityManager;

    private static final String VARIANT_STOCK = """
        WITH variant_stock AS (
          SELECT v.product_id, v.variant_id, v.status, v.price,v.low_stock_threshold,v.sku,v.variant_name,
            COALESCE(SUM(CAST(b.quantity_on_hand AS BIGINT)),0) AS physical,
            COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date>=:today)
              AND b.quantity_on_hand>b.reserved_quantity
              THEN CAST(b.quantity_on_hand AS BIGINT)-b.reserved_quantity ELSE 0 END),0) AS available
          FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id
          LEFT JOIN dbo.inventory_batches b ON b.variant_id=v.variant_id
          WHERE p.shop_id=:shop __scope__
          GROUP BY v.product_id,v.variant_id,v.status,v.price,v.low_stock_threshold,v.sku,v.variant_name
        )
        """;

    private static final String CLASSIFICATION = VARIANT_STOCK.replace("__scope__", "AND p.status<>'DELETED'") + """
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

    private Query query(String sql, Long shop, List<Long> categories, LocalDate today) {
        return query(sql, "", shop, categories, today);
    }

    private Query query(String sql, String scope, Long shop, List<Long> categories, LocalDate today) {
        String classification = CLASSIFICATION.replace("p.shop_id=:shop AND p.status<>'DELETED'",
                "p.shop_id=:shop AND p.status<>'DELETED'" + scope);
        Query query = entityManager.createNativeQuery(classification + sql);
        bindBase(query, shop, categories, today);
        return query;
    }

    private void bindBase(Query query, Long shop, List<Long> categories, LocalDate today) {
        query.setParameter("shop", shop);
        query.setParameter("today", today);
        query.setParameter("categories", categories.isEmpty() ? List.of(-1L) : categories);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Product> searchManagement(Long shop, String text, Long category, String tab,
            LocalDateTime from, LocalDateTime to, List<Long> categories, LocalDate today, Pageable page) {
        ProductCatalogFilter filters = new ProductCatalogFilter();
        filters.setQ(text);
        filters.setCategory(category);
        filters.setTab(tab);
        return searchManagement(shop, filters, from, to, categories, today, page);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Product> searchManagement(Long shop, ProductCatalogFilter filters,
            LocalDateTime from, LocalDateTime to, List<Long> categories, LocalDate today, Pageable page) {
        String text = filters.getQ();
        Long category = filters.getCategory();
        String tab = filters.getTab();
        String where = " FROM dbo.products p JOIN classified c ON c.product_id=p.product_id JOIN product_signals ps ON ps.product_id=p.product_id WHERE 1=1";
        if (!tab.isEmpty()) where += " AND c.management_state=:tab";
        if (category != null) where += " AND EXISTS (SELECT 1 FROM dbo.product_categories pc WHERE pc.product_id=p.product_id AND pc.category_id=:category)";
        if (from != null) where += " AND p.created_at>=:from";
        if (to != null) where += " AND p.created_at<:to";
        where += switch (filters.getStock()) {
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
        if (!filters.getApproval().isEmpty()) where += " AND p.approval_status=:approval";
        if (!filters.getEligibility().isEmpty()) where += " AND c.management_state=:eligibility";
        if (filters.getMinPrice() != null || filters.getMaxPrice() != null) {
            where += " AND EXISTS (SELECT 1 FROM dbo.product_variants pv WHERE pv.product_id=p.product_id AND pv.status<>'DELETED'";
            if (filters.getMinPrice() != null) where += " AND pv.price>=:minPrice";
            if (filters.getMaxPrice() != null) where += " AND pv.price<=:maxPrice";
            where += ")";
        }
        if (!text.isEmpty()) where += " " + """
            AND ((:productCode<0 AND :batchCode<0 AND (LOWER(p.name) LIKE :text OR LOWER(p.slug) LIKE :text
              OR EXISTS (SELECT 1 FROM dbo.product_variants v WHERE v.product_id=p.product_id
                AND (LOWER(v.sku) LIKE :text OR LOWER(v.variant_name) LIKE :text))))
              OR p.product_id=:productCode
              OR EXISTS (SELECT 1 FROM dbo.inventory_batches b JOIN dbo.product_variants bv ON bv.variant_id=b.variant_id
                WHERE bv.product_id=p.product_id AND b.batch_id=:batchCode))
            """;
        Query count = query("SELECT COUNT(*)" + where, shop, categories, today);
        bindFilters(count, text, category, tab, from, to);
        bindAdvancedFilters(count, filters);
        long total = ((Number) count.getSingleResult()).longValue();
        String direction = "updated_asc".equals(filters.getSort()) ? " ASC" : " DESC";
        Query rows = entityManager.createNativeQuery(CLASSIFICATION + "SELECT p.*" + where
                + " ORDER BY COALESCE(p.updated_at,p.created_at)" + direction + ",p.product_id" + direction, Product.class);
        bindBase(rows, shop, categories, today);
        bindFilters(rows, text, category, tab, from, to);
        bindAdvancedFilters(rows, filters);
        rows.setFirstResult(Math.toIntExact(page.getOffset()));
        rows.setMaxResults(page.getPageSize());
        return new PageImpl<>((List<Product>) rows.getResultList(), page, total);
    }

    private void bindAdvancedFilters(Query query, ProductCatalogFilter filters) {
        if (!filters.getApproval().isEmpty()) query.setParameter("approval", filters.getApproval());
        if (!filters.getEligibility().isEmpty()) query.setParameter("eligibility", filters.getEligibility());
        if (filters.getMinPrice() != null) query.setParameter("minPrice", filters.getMinPrice());
        if (filters.getMaxPrice() != null) query.setParameter("maxPrice", filters.getMaxPrice());
    }

    private void bindFilters(Query query, String text, Long category, String tab, LocalDateTime from, LocalDateTime to) {
        if (!tab.isEmpty()) query.setParameter("tab", tab);
        if (category != null) query.setParameter("category", category);
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);
        if (!text.isEmpty()) {
            query.setParameter("text", "%" + text.toLowerCase(java.util.Locale.ROOT) + "%");
            Long productCode = BusinessCodes.parse("PRD", text);
            Long batchCode = BusinessCodes.parse("LOT", text);
            query.setParameter("productCode", productCode == null ? -1L : productCode);
            query.setParameter("batchCode", batchCode == null ? -1L : batchCode);
        }
    }

    @Override
    public ProductTabSummary managementSummary(Long shop, List<Long> categories, LocalDate today) {
        Object[] result = (Object[]) query("""
            SELECT COUNT(*),
              COALESCE(SUM(CASE WHEN management_state='active' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='out-of-stock' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='violation' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='hidden' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='unavailable' THEN CAST(1 AS BIGINT) ELSE 0 END),0)
            FROM classified
            """, shop, categories, today).getSingleResult();
        return new ProductTabSummary(number(result[0]), number(result[1]), number(result[2]),
                number(result[3]), number(result[4]), number(result[5]));
    }

    @Override
    public Map<Long, ProductManagementState> managementStates(Long shop, List<Long> ids, List<Long> categories, LocalDate today) {
        Map<Long, ProductManagementState> states = new HashMap<>();
        if (ids.isEmpty()) return states;
        Query query = query("""
                SELECT c.product_id,c.management_state,c.state_reason,
                  CASE WHEN p.status='INACTIVE' AND p.moderation_blocked=0 AND p.has_category=1 AND p.has_open_variant=1 THEN 1 ELSE 0 END,
                  CASE WHEN p.status='ACTIVE' THEN 1 ELSE 0 END,
                  CASE WHEN p.moderation_blocked=1 THEN N'Sản phẩm đang bị Admin khóa.'
                    WHEN p.has_category=0 THEN N'Sản phẩm chưa có danh mục còn hoạt động.'
                    WHEN p.has_open_variant=0 THEN N'Sản phẩm cần có ít nhất một phân loại đang bật để mở bán.'
                    ELSE '' END
                FROM classified c JOIN product_signals p ON p.product_id=c.product_id
                """,
                " AND p.product_id IN (:ids)", shop, categories, today);
        query.setParameter("ids", ids);
        for (Object value : query.getResultList()) {
            Object[] row = (Object[]) value;
            states.put(number(row[0]), new ProductManagementState((String) row[1], (String) row[2],
                    number(row[3]) == 1, number(row[4]) == 1, (String) row[5]));
        }
        return states;
    }

    @Override
    public boolean canSellVariant(Long shop, Long variant, List<Long> categories, LocalDate today) {
        Query query = query("""
            SELECT COUNT(*) FROM product_signals p JOIN variant_stock v ON v.product_id=p.product_id
            WHERE v.variant_id=:variant AND p.status='ACTIVE' AND p.moderation_blocked=0
              AND p.shop_open=1 AND p.has_category=1 AND v.status='ACTIVE'
              AND v.price>=10000 AND v.price=FLOOR(v.price)
            """, " AND p.product_id=(SELECT product_id FROM dbo.product_variants WHERE variant_id=:variant)", shop, categories, today);
        query.setParameter("variant", variant);
        return number(query.getSingleResult()) > 0;
    }


    private String inventoryCriteria(VietFreshHub.inventory.dto.InventoryCatalogFilter f) {
        String where = "v.product_id=p.product_id";
        // The checkbox broadens the catalog scope; it does not select only deleted stock.
        if (!f.isArchived() && f.getVariantId() == null) where += " AND p.status<>'DELETED' AND v.status<>'DELETED'";
        if (f.getCategory() != null) where += " AND EXISTS (SELECT 1 FROM dbo.product_categories pc WHERE pc.product_id=p.product_id AND pc.category_id=:category)";
        if (f.getVariantId() != null) where += " AND v.variant_id=:variant";
        where += switch(f.getStock()) {
            case "available" -> " AND v.available>0";
            case "low" -> " AND v.available>0 AND v.available<=COALESCE(v.low_stock_threshold,0)";
            case "empty" -> " AND v.available=0";
            default -> "";
        };
        if (!f.getExpiry().isEmpty()) where += " AND EXISTS (SELECT 1 FROM dbo.inventory_batches eb WHERE eb.variant_id=v.variant_id AND eb.quantity_on_hand>0 AND " + switch(f.getExpiry()) {
            case "near" -> "eb.expiry_date>=:today AND eb.expiry_date<=DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE))";
            case "expired" -> "eb.expiry_date<:today";
            case "valid" -> "(eb.expiry_date IS NULL OR eb.expiry_date>DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE)))";
            case "attention" -> "eb.expiry_date<=DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE))";
            default -> throw new IllegalArgumentException("Invalid expiry filter");
        } + ")";
        if (!f.getQ().isEmpty()) where += """
             AND ((:productCode<0 AND :batchCode<0 AND (LOWER(p.name) LIKE :text OR LOWER(p.slug) LIKE :text
                 OR LOWER(v.sku) LIKE :text OR LOWER(v.variant_name) LIKE :text))
                 OR p.product_id=:productCode OR EXISTS (SELECT 1 FROM dbo.inventory_batches qb
                   WHERE qb.variant_id=v.variant_id AND qb.batch_id=:batchCode))
            """;
        return where;
    }

    private Query inventoryQuery(String sql, Long shop, VietFreshHub.inventory.dto.InventoryCatalogFilter f,
            LocalDate today, boolean entities) {
        String stock = VARIANT_STOCK.replace("__scope__", "");
        Query query = entities ? entityManager.createNativeQuery(stock + sql, Product.class)
                : entityManager.createNativeQuery(stock + sql);
        query.setParameter("shop", shop);
        query.setParameter("today", today);
        bindFilters(query, f.getQ(), f.getCategory(), "", null, null);
        if (f.getVariantId() != null) query.setParameter("variant", f.getVariantId());
        return query;
    }

    @Override @SuppressWarnings("unchecked")
    public Page<Product> searchInventory(Long shop, VietFreshHub.inventory.dto.InventoryCatalogFilter f,
            LocalDate today, Pageable page) {
        String where = " FROM dbo.products p WHERE p.shop_id=:shop AND EXISTS (SELECT 1 FROM variant_stock v WHERE " + inventoryCriteria(f) + ")";
        long count = number(inventoryQuery("SELECT COUNT(*)" + where, shop, f, today, false).getSingleResult());
        Query rows = inventoryQuery("SELECT p.*" + where + " ORDER BY p.product_id DESC", shop, f, today, true);
        rows.setFirstResult(Math.toIntExact(page.getOffset()));
        rows.setMaxResults(page.getPageSize());
        return new PageImpl<>((List<Product>)rows.getResultList(), page, count);
    }

    @Override
    public List<Long> matchingInventoryVariants(Long shop, VietFreshHub.inventory.dto.InventoryCatalogFilter f,
            LocalDate today, List<Long> products) {
        if (products.isEmpty()) return List.of();
        Query query = inventoryQuery("SELECT v.variant_id FROM variant_stock v JOIN dbo.products p ON p.product_id=v.product_id WHERE p.product_id IN (:products) AND " + inventoryCriteria(f), shop, f, today, false);
        query.setParameter("products", products);
        return query.getResultList().stream().map(value -> number(value)).toList();
    }

    private long number(Object value) { return ((Number) value).longValue(); }

    @Override
    public VietFreshHub.inventory.dto.InventoryAttentionSummary inventoryAttentionSummary(Long shop, LocalDate today) {
        Query query = entityManager.createNativeQuery(VARIANT_STOCK.replace("__scope__", "AND p.status<>'DELETED'") + """
            SELECT (SELECT COUNT(*) FROM variant_stock v WHERE v.status<>'DELETED'
                AND v.available>0 AND v.available<=COALESCE(v.low_stock_threshold,0)),
              (SELECT COUNT(*) FROM dbo.inventory_batches b JOIN variant_stock v ON v.variant_id=b.variant_id
                JOIN dbo.products p ON p.product_id=v.product_id WHERE v.status<>'DELETED' AND b.quantity_on_hand>0
                AND b.expiry_date>=:today AND b.expiry_date<=DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE))),
              (SELECT COUNT(*) FROM dbo.inventory_batches b JOIN variant_stock v ON v.variant_id=b.variant_id
                WHERE v.status<>'DELETED' AND b.quantity_on_hand>0 AND b.expiry_date<:today)
            """);
        query.setParameter("shop", shop);
        query.setParameter("today", today);
        Object[] result = (Object[])query.getSingleResult();
        return new VietFreshHub.inventory.dto.InventoryAttentionSummary(number(result[0]), number(result[1]), number(result[2]));
    }
}
