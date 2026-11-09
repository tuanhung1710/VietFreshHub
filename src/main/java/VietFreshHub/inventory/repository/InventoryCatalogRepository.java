package VietFreshHub.inventory.repository;

import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.Pagination;
import VietFreshHub.inventory.dto.InventoryCatalogFilter;
import VietFreshHub.inventory.dto.InventoryCatalogRow;
import VietFreshHub.inventory.dto.InventoryCatalogSummary;
import VietFreshHub.inventory.dto.RestockChoice;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Truy vấn màn kho theo lô hoặc theo SKU. Tổng tồn được gom theo SKU trước khi nối với sản phẩm;
 * bộ lọc ngày nhập và trạng thái lô chỉ áp dụng ở chế độ lô để không làm sai tổng tồn SKU.
 */
@Repository
@RequiredArgsConstructor
public class InventoryCatalogRepository {
    private static final String STOCK = InventoryStockSql.VARIANT_STOCK.replace("__scope__", "");
    private static final String VARIANTS =
            " FROM variant_stock v JOIN dbo.products p ON p.product_id=v.product_id ";
    private static final String BATCHES =
            " JOIN dbo.inventory_batches b ON b.variant_id=v.variant_id ";
    private static final String ACTIVE = " AND p.status<>'DELETED' AND v.status<>'DELETED'";
    private static final String NEAR =
            "b.expiry_date>=:today AND DATEDIFF(DAY,:today,b.expiry_date)<=COALESCE(p.expiry_warning_days,3)";
    private static final String LOT_CODE =
            """
            LOWER(CONCAT('LOT-', CASE WHEN b.batch_id<1000000
                THEN RIGHT(CONCAT('000000',b.batch_id),6) ELSE CAST(b.batch_id AS VARCHAR(20)) END))
            """;
    private static final String SELECT =
            """
            SELECT p.product_id,p.name,v.variant_id,v.variant_name,v.sku,v.unit,v.batches_in_stock,
                CASE WHEN v.available>0 AND v.available<=COALESCE(v.low_stock_threshold,0) THEN 1 ELSE 0 END AS low_stock,
            """;
    private static final String SKU_COLUMNS =
            "v.physical,v.reserved,v.available,v.expired_quantity";
    private static final String BATCH_COLUMNS =
            """
            b.batch_id,b.received_at,b.expiry_date,b.quantity_on_hand AS physical,b.reserved_quantity AS reserved,
            CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date>=:today)
                AND b.quantity_on_hand>b.reserved_quantity THEN b.quantity_on_hand-b.reserved_quantity ELSE 0 END AS available,
            CASE WHEN b.status='BLOCKED' THEN 1 ELSE 0 END AS blocked,
            CASE WHEN b.quantity_on_hand>0 AND b.expiry_date<:today THEN 1 ELSE 0 END AS expired,
            CASE WHEN b.quantity_on_hand>0 AND %s THEN 1 ELSE 0 END AS near_expiry
            """
                    .formatted(NEAR);
    private final NamedParameterJdbcTemplate jdbc;

    /** Dùng chung điều kiện cho tổng đếm và dữ liệu trang; đưa trang vượt giới hạn về trang cuối. */
    public Page<InventoryCatalogRow> search(
            Long shop, InventoryCatalogFilter filters, LocalDate today, int page, int size) {
        var parameters = parameters(shop, today);
        String scope = scope(filters, parameters);
        long total = jdbc.queryForObject(STOCK + "SELECT COUNT(*)" + scope, parameters, Long.class);
        PageRequest pageable = Pagination.clamp(page, size, total);
        return new PageImpl<>(
                rows(filters, scope, parameters, pageable.getOffset(), pageable.getPageSize()),
                pageable,
                total);
    }

    /** Mỗi SKU một lựa chọn nhập mới; không nối lô hoặc yêu cầu đã có tồn kho. */
    public List<RestockChoice> restockChoices(Long shop, String query, int limit) {
        var parameters = new MapSqlParameterSource("shop", shop).addValue("limit", limit);
        var sql = new StringBuilder(
                """
                SELECT p.product_id,p.name,v.variant_id,v.variant_name,v.sku,v.unit
                FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id
                WHERE p.shop_id=:shop
                """).append(ACTIVE);
        if (!query.isEmpty()) {
            Long productId = BusinessCodes.parse("PRD", query);
            if (productId != null) {
                sql.append(" AND p.product_id=:productCode");
                parameters.addValue("productCode", productId);
            } else {
                sql.append(
                        " AND (LOWER(p.name) LIKE :text ESCAPE '!' OR LOWER(p.slug) LIKE :text ESCAPE '!'"
                                + " OR LOWER(v.sku) LIKE :text ESCAPE '!' OR LOWER(v.variant_name) LIKE :text ESCAPE '!')");
                parameters.addValue("text", searchPattern(query));
            }
        }
        sql.append(" ORDER BY p.product_id DESC,v.variant_id DESC OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY");
        return jdbc.query(
                sql.toString(),
                parameters,
                (row, index) -> new RestockChoice(
                        row.getLong("product_id"),
                        row.getLong("variant_id"),
                        row.getString("name"),
                        row.getString("variant_name"),
                        row.getString("sku"),
                        row.getString("unit")));
    }

    /** Tổng quan toàn cửa hàng, độc lập từ khóa và trang đang xem. */
    public InventoryCatalogSummary summary(Long shop, LocalDate today) {
        String variants = VARIANTS + " WHERE p.shop_id=:shop" + ACTIVE;
        String batches = VARIANTS + BATCHES + " WHERE p.shop_id=:shop" + ACTIVE;
        return jdbc.queryForObject(
                STOCK
                        + """
                SELECT (SELECT COUNT(*) %s) AS skus, COUNT(*) AS total_batches,
                    COUNT(CASE WHEN b.quantity_on_hand>0 THEN 1 END) AS batches_in_stock,
                    COUNT(CASE WHEN b.quantity_on_hand>0 AND %s THEN 1 END) AS near_batches,
                    COUNT(CASE WHEN b.quantity_on_hand>0 AND b.expiry_date<:today THEN 1 END) AS expired_batches
                %s
                """
                                .formatted(variants, NEAR, batches),
                parameters(shop, today),
                (row, index) ->
                        new InventoryCatalogSummary(
                                row.getLong("skus"),
                                row.getLong("total_batches"),
                                row.getLong("batches_in_stock"),
                                row.getLong("near_batches"),
                                row.getLong("expired_batches")));
    }

    /** Kiểm tra SKU thuộc cửa hàng trước khi cho lọc trực tiếp bằng variantId từ URL. */
    public boolean ownsVariant(Long shop, Long variant) {
        return jdbc.queryForObject(
                        """
                SELECT COUNT(*) FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id
                WHERE p.shop_id=:shop AND v.variant_id=:variant
                """,
                        new MapSqlParameterSource("shop", shop).addValue("variant", variant),
                        Long.class)
                > 0;
    }

    private List<InventoryCatalogRow> rows(
            InventoryCatalogFilter filters,
            String scope,
            MapSqlParameterSource parameters,
            long offset,
            int limit) {
        boolean sku = filters.isSkuView();
        String order =
                sku
                        ? " ORDER BY p.product_id DESC,v.variant_id DESC"
                        : " ORDER BY b.received_at DESC,b.batch_id DESC";
        parameters.addValue("offset", offset).addValue("limit", limit);
        return jdbc.query(
                STOCK
                        + SELECT
                        + (sku ? SKU_COLUMNS : BATCH_COLUMNS)
                        + scope
                        + order
                        + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
                parameters,
                (row, index) -> mapRow(row, sku));
    }

    private InventoryCatalogRow mapRow(ResultSet row, boolean sku) throws SQLException {
        var received = sku ? null : row.getTimestamp("received_at");
        var expires = sku ? null : row.getDate("expiry_date");
        return new InventoryCatalogRow(
                sku ? null : row.getLong("batch_id"),
                row.getLong("product_id"),
                row.getLong("variant_id"),
                row.getString("name"),
                row.getString("variant_name"),
                row.getString("sku"),
                row.getString("unit"),
                received == null ? null : received.toLocalDateTime().toLocalDate(),
                expires == null ? null : expires.toLocalDate(),
                row.getLong("physical"),
                row.getLong("reserved"),
                row.getLong("available"),
                row.getLong("batches_in_stock"),
                sku ? row.getLong("expired_quantity") : 0,
                !sku && row.getBoolean("expired"),
                !sku && row.getBoolean("near_expiry"),
                !sku && row.getBoolean("blocked"),
                row.getBoolean("low_stock"));
    }

    private String scope(InventoryCatalogFilter filters, MapSqlParameterSource parameters) {
        boolean sku = filters.isSkuView();
        var sql =
                new StringBuilder(VARIANTS)
                        .append(sku ? "" : BATCHES)
                        .append(" WHERE p.shop_id=:shop");
        if (filters.getVariantId() == null) {
            sql.append(ACTIVE);
        }
        if (filters.getVariantId() != null) {
            sql.append(" AND v.variant_id=:variant");
            parameters.addValue("variant", filters.getVariantId());
        }
        appendSearch(filters.getQ(), sku, parameters, sql);
        if (sku) {
            sql.append(
                    switch (filters.getStock()) {
                        case "enough" ->
                                " AND v.available>COALESCE(v.low_stock_threshold,0) AND v.available>0";
                        case "low" ->
                                " AND v.available>0 AND v.available<=COALESCE(v.low_stock_threshold,0)";
                        case "empty" -> " AND v.available=0";
                        default -> "";
                    });
        } else {
            appendBatchFilters(filters, parameters, sql);
        }
        return sql.toString();
    }

    private void appendSearch(
            String text, boolean sku, MapSqlParameterSource parameters, StringBuilder sql) {
        if (text.isEmpty()) {
            return;
        }
        Long productId = BusinessCodes.parse("PRD", text);
        if (productId != null) {
            sql.append(" AND p.product_id=:productCode");
            parameters.addValue("productCode", productId);
            return;
        }
        Long batchId = BusinessCodes.parse("LOT", text);
        if (batchId != null) {
            sql.append(
                    sku
                            ? " AND EXISTS (SELECT 1 FROM dbo.inventory_batches b WHERE b.variant_id=v.variant_id AND b.batch_id=:batchCode)"
                            : " AND b.batch_id=:batchCode");
            parameters.addValue("batchCode", batchId);
            return;
        }
        String matchLot = LOT_CODE + " LIKE :text ESCAPE '!'";
        sql.append(
                " AND (LOWER(p.name) LIKE :text ESCAPE '!' OR LOWER(p.slug) LIKE :text ESCAPE '!' OR LOWER(v.sku) LIKE :text ESCAPE '!'"
                        + " OR LOWER(v.variant_name) LIKE :text ESCAPE '!' OR ");
        sql.append(
                        sku
                                ? "EXISTS (SELECT 1 FROM dbo.inventory_batches b WHERE b.variant_id=v.variant_id AND "
                                        + matchLot
                                        + ")"
                                : matchLot)
                .append(")");
        parameters.addValue("text", searchPattern(text));
    }

    private String searchPattern(String text) {
        return "%"
                + text.toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                        .replace("[", "![")
                + "%";
    }

    private void appendBatchFilters(
            InventoryCatalogFilter filters, MapSqlParameterSource parameters, StringBuilder sql) {
        if ("empty".equals(filters.getExpiry())) {
            sql.append(" AND b.quantity_on_hand=0");
        } else if (!filters.getExpiry().isEmpty()) {
            sql.append(" AND b.quantity_on_hand>0 AND (")
                    .append(
                            switch (filters.getExpiry()) {
                                case "near" -> NEAR;
                                case "expired" -> "b.expiry_date<:today";
                                case "valid" ->
                                        "b.expiry_date>=:today AND DATEDIFF(DAY,:today,b.expiry_date)>COALESCE(p.expiry_warning_days,3)";
                                default -> throw new IllegalArgumentException("Trạng thái lô không hợp lệ.");
                            })
                    .append(")");
        }
        if (filters.getReceivedFrom() != null) {
            sql.append(" AND b.received_at>=:receivedFrom");
            parameters.addValue(
                    "receivedFrom", Timestamp.valueOf(filters.getReceivedFrom().atStartOfDay()));
        }
        // Mốc kết thúc không bao gồm ngày tiếp theo; riêng ngày cuối miền SQL không cộng thêm ngày.
        if (filters.getReceivedTo() != null
                && !filters.getReceivedTo().equals(LocalDate.of(9999, 12, 31))) {
            sql.append(" AND b.received_at<:receivedTo");
            parameters.addValue(
                    "receivedTo",
                    Timestamp.valueOf(filters.getReceivedTo().plusDays(1).atStartOfDay()));
        }
    }

    private MapSqlParameterSource parameters(Long shop, LocalDate today) {
        return new MapSqlParameterSource("shop", shop).addValue("today", Date.valueOf(today));
    }
}
