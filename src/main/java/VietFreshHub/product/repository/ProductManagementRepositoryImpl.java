package VietFreshHub.product.repository;

import static VietFreshHub.inventory.repository.InventoryStockSql.VARIANT_STOCK;
import static VietFreshHub.product.repository.ProductCatalogSql.CLASSIFICATION;

import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.VietnamDatabaseTime;
import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.entity.Product;

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
import java.util.Locale;
import java.util.Map;

/**
 * Dùng chung cách phân loại trạng thái cho tìm kiếm, thống kê, hiển thị và điều kiện đặt mua.
 * Tổng hợp tồn theo SKU trước rồi mới theo sản phẩm để tránh đếm trùng sản phẩm.
 * Tổng hợp cuối chỉ dùng cột đã tính vì SQL Server không cho đặt truy vấn con trong SUM.
 */
public class ProductManagementRepositoryImpl implements ProductManagementRepository {
    @PersistenceContext private EntityManager entityManager;

    /** Tạo truy vấn SQL dùng chung cách phân loại trạng thái sản phẩm của cửa hàng. */
    private Query query(String sql, Long shop, List<Long> categories, LocalDate today) {
        return query(sql, "", shop, categories, today);
    }

    /**
     * Ghép điều kiện giới hạn sản phẩm vào phần phân loại trước khi tạo truy vấn và gán tham số chung.
     */
    private Query query(
            String sql, String scope, Long shop, List<Long> categories, LocalDate today) {
        // Đẩy giới hạn ID vào phần tổng hợp để chỉ tính trạng thái của các sản phẩm cần xử lý.
        String classification =
                CLASSIFICATION.replace(
                        "p.shop_id=:shop AND p.status<>'DELETED'",
                        "p.shop_id=:shop AND p.status<>'DELETED'" + scope);
        Query query = entityManager.createNativeQuery(classification + sql);
        bindBase(query, shop, categories, today);
        return query;
    }

    /** Gán cửa hàng, ngày và danh mục hợp lệ; dùng ID không tồn tại khi danh sách danh mục rỗng. */
    private void bindBase(Query query, Long shop, List<Long> categories, LocalDate today) {
        query.setParameter("shop", shop);
        query.setParameter("today", today);
        // IN không nhận danh sách rỗng; ID -1 bảo đảm không khớp danh mục nào.
        query.setParameter("categories", categories.isEmpty() ? List.of(-1L) : categories);
    }

    /**
     * Lọc sản phẩm theo trạng thái quản lý và điều kiện nâng cao, dùng cùng điều kiện cho tổng đếm và dữ liệu trang.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Page<Product> searchManagement(
            Long shop,
            ProductCatalogFilter filters,
            LocalDateTime from,
            LocalDateTime to,
            List<Long> categories,
            LocalDate today,
            Pageable page) {
        String text = filters.getQ();
        Long category = filters.getCategory();
        String tab = filters.getTab();
        String where = ProductCatalogSql.managementWhere(filters, from, to);
        // Đếm toàn bộ kết quả khớp trước khi giới hạn số dòng của trang hiện tại.
        Query count = query("SELECT COUNT(*)" + where, shop, categories, today);
        bindFilters(count, text, category, tab, from, to);
        bindAdvancedFilters(count, filters);
        long total = ((Number) count.getSingleResult()).longValue();
        String direction = "updated_asc".equals(filters.getSort()) ? " ASC" : " DESC";
        Query rows =
                entityManager.createNativeQuery(
                        CLASSIFICATION
                                + "SELECT p.*"
                                + where
                                + " ORDER BY COALESCE(p.updated_at,p.created_at)"
                                + direction
                                + ",p.product_id"
                                + direction,
                        Product.class);
        bindBase(rows, shop, categories, today);
        bindFilters(rows, text, category, tab, from, to);
        bindAdvancedFilters(rows, filters);
        // Sắp thêm theo ID để các bản ghi cùng thời điểm không đổi vị trí giữa những lần phân
        // trang.
        rows.setFirstResult(Math.toIntExact(page.getOffset()));
        rows.setMaxResults(page.getPageSize());
        return new PageImpl<>((List<Product>) rows.getResultList(), page, total);
    }

    /**
     * Chỉ gán tham số khoảng giá khi bộ lọc tương ứng được dùng.
     */
    private void bindAdvancedFilters(Query query, ProductCatalogFilter filters) {
        if (filters.getMinPrice() != null) {
            query.setParameter("minPrice", filters.getMinPrice());
        }
        if (filters.getMaxPrice() != null) {
            query.setParameter("maxPrice", filters.getMaxPrice());
        }
    }

    /**
     * Gán tham số tìm kiếm cơ bản; phân biệt từ khóa thông thường với mã sản phẩm PRD và mã lô LOT.
     */
    private void bindFilters(
            Query query,
            String text,
            Long category,
            String tab,
            LocalDateTime from,
            LocalDateTime to) {
        if (!tab.isEmpty()) {
            query.setParameter("tab", tab);
        }
        if (category != null) {
            query.setParameter("category", category);
        }
        if (from != null) {
            query.setParameter("from", VietnamDatabaseTime.toDatabase(from));
        }
        if (to != null) {
            query.setParameter("to", VietnamDatabaseTime.toDatabase(to));
        }
        if (!text.isEmpty()) {
            // Từ khóa thường dùng LIKE; mã PRD/LOT được tách ID để tìm chính xác đúng đối tượng.
            query.setParameter("text", "%" + text.toLowerCase(Locale.ROOT) + "%");
            Long productCode = BusinessCodes.parse("PRD", text);
            Long batchCode = BusinessCodes.parse("LOT", text);
            query.setParameter("productCode", productCode == null ? -1L : productCode);
            query.setParameter("batchCode", batchCode == null ? -1L : batchCode);
        }
    }

    /** Đếm sản phẩm theo cùng quy tắc phân loại với danh sách để các số thống kê nhất quán. */
    @Override
    public ProductTabSummary managementSummary(Long shop, List<Long> categories, LocalDate today) {
        // Mỗi dòng classified là một sản phẩm nên các phép SUM không bị đếm lặp theo số SKU hoặc
        // lô.
        Object[] result =
                (Object[])
                        query(
                                        """
            SELECT COUNT(*),
              COALESCE(SUM(CASE WHEN management_state='active' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='out-of-stock' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='violation' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='hidden' THEN CAST(1 AS BIGINT) ELSE 0 END),0),
              COALESCE(SUM(CASE WHEN management_state='unavailable' THEN CAST(1 AS BIGINT) ELSE 0 END),0)
            FROM classified
            """,
                                        shop,
                                        categories,
                                        today)
                                .getSingleResult();
        return new ProductTabSummary(
                number(result[0]),
                number(result[1]),
                number(result[2]),
                number(result[3]),
                number(result[4]),
                number(result[5]));
    }

    /**
     * Lấy trạng thái, lý do và khả năng mở bán hoặc tạm ẩn cho các sản phẩm được chọn trong cửa hàng.
     */
    @Override
    public Map<Long, ProductManagementState> managementStates(
            Long shop, List<Long> ids, List<Long> categories, LocalDate today) {
        Map<Long, ProductManagementState> states = new HashMap<>();
        if (ids.isEmpty()) {
            return states;
        }
        Query query =
                query(
                        """
                SELECT c.product_id,c.management_state,c.state_reason,
                  CASE WHEN p.status='INACTIVE' AND p.moderation_blocked=0 AND p.has_category=1 AND p.has_open_variant=1 THEN 1 ELSE 0 END,
                  CASE WHEN p.status='ACTIVE' THEN 1 ELSE 0 END,
                  CASE WHEN p.moderation_blocked=1 THEN N'Sản phẩm đang bị Admin khóa.'
                    WHEN p.has_category=0 THEN N'Sản phẩm chưa có danh mục còn hoạt động.'
                    WHEN p.has_open_variant=0 THEN N'Sản phẩm cần có ít nhất một phân loại đang bật để mở bán.'
                    ELSE '' END
                FROM classified c JOIN product_signals p ON p.product_id=c.product_id
                """,
                        " AND p.product_id IN (:ids)",
                        shop,
                        categories,
                        today);
        query.setParameter("ids", ids);
        // Thứ tự cột SQL tương ứng: ID, trạng thái, lý do, được mở bán, được ẩn, lý do chặn mở bán.
        for (Object value : query.getResultList()) {
            Object[] row = (Object[]) value;
            states.put(
                    number(row[0]),
                    new ProductManagementState(
                            (String) row[1],
                            (String) row[2],
                            number(row[3]) == 1,
                            number(row[4]) == 1,
                            (String) row[5]));
        }
        return states;
    }

    /**
     * Kiểm tra điều kiện bán theo trạng thái shop, sản phẩm, danh mục, phân loại và giá hợp lệ.
     * Lượng tồn khả dụng được kiểm tra riêng khi phân bổ hàng.
     */
    @Override
    public boolean canSellVariant(Long shop, Long variant, List<Long> categories, LocalDate today) {
        Query query =
                query(
                        """
            SELECT COUNT(*) FROM product_signals p JOIN variant_stock v ON v.product_id=p.product_id
            WHERE v.variant_id=:variant AND p.status='ACTIVE' AND p.moderation_blocked=0
              AND p.shop_open=1 AND p.has_category=1 AND v.status='ACTIVE'
              AND v.price>=10000 AND v.price=FLOOR(v.price)
            """,
                        " AND p.product_id=(SELECT product_id FROM dbo.product_variants WHERE variant_id=:variant)",
                        shop,
                        categories,
                        today);
        query.setParameter("variant", variant);
        return number(query.getSingleResult()) > 0;
    }

    /**
     * Đổi giá trị số do truy vấn SQL trả về thành long để dùng thống nhất trong kết quả tổng hợp.
     */
    private long number(Object value) {
        return ((Number) value).longValue();
    }

    /**
     * Đếm phân loại tồn thấp và lô còn hàng cận hạn, hết hạn trên toàn cửa hàng đang kinh doanh.
     */
    @Override
    public InventoryAttentionSummary inventoryAttentionSummary(Long shop, LocalDate today) {
        // Ba số có đơn vị đếm khác nhau: SKU tồn thấp, lô cận hạn và lô hết hạn còn hàng.
        Query query =
                entityManager.createNativeQuery(
                        VARIANT_STOCK.replace("__scope__", "AND p.status<>'DELETED'")
                                + """
            SELECT (SELECT COUNT(*) FROM variant_stock v WHERE v.status<>'DELETED'
                AND v.available>0 AND v.available<=COALESCE(v.low_stock_threshold,0)),
              (SELECT COUNT(*) FROM dbo.inventory_batches b JOIN variant_stock v ON v.variant_id=b.variant_id
                JOIN dbo.products p ON p.product_id=v.product_id WHERE v.status<>'DELETED' AND b.quantity_on_hand>0
                AND b.expiry_date>=:today AND b.expiry_date<=DATEADD(day,COALESCE(p.expiry_warning_days,3),CAST(:today AS DATE))),
              (SELECT COUNT(*) FROM dbo.inventory_batches b JOIN variant_stock v ON v.variant_id=b.variant_id
                WHERE v.status<>'DELETED' AND b.quantity_on_hand>0 AND b.expiry_date<:today)
            ,(SELECT COUNT(*) FROM dbo.inventory_batches b JOIN variant_stock v ON v.variant_id=b.variant_id
                WHERE v.status<>'DELETED')
            """);
        query.setParameter("shop", shop);
        query.setParameter("today", today);
        Object[] result = (Object[]) query.getSingleResult();
        return new VietFreshHub.inventory.dto.InventoryAttentionSummary(
                number(result[0]), number(result[1]), number(result[2]), number(result[3]));
    }
}
