package VietFreshHub.inventory.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InventoryAlertRepository {
    public record Alert(Long entityId, String title, String content) {}

    private final NamedParameterJdbcTemplate jdbc;

    public List<Alert> lowStock(Long shopId, LocalDate today) {
        return jdbc.query(
                """
                SELECT v.variant_id,p.name,v.variant_name,v.low_stock_threshold,
                    COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date>=:today)
                        AND b.quantity_on_hand>b.reserved_quantity THEN CAST(b.quantity_on_hand AS BIGINT)-b.reserved_quantity ELSE 0 END),0) AS stock
                FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id
                LEFT JOIN dbo.inventory_batches b ON b.variant_id=v.variant_id
                WHERE p.shop_id=:shop AND p.status<>'DELETED' AND v.status IN ('ACTIVE','OUT_OF_STOCK')
                GROUP BY v.variant_id,p.name,v.variant_name,v.low_stock_threshold
                HAVING COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date>=:today)
                        AND b.quantity_on_hand>b.reserved_quantity THEN CAST(b.quantity_on_hand AS BIGINT)-b.reserved_quantity ELSE 0 END),0)<=COALESCE(v.low_stock_threshold,0)
                """,
                new MapSqlParameterSource("shop", shopId).addValue("today", today),
                (row, index) ->
                        new Alert(
                                row.getLong("variant_id"),
                                "Cảnh báo tồn kho thấp",
                                row.getString("name")
                                        + " · "
                                        + row.getString("variant_name")
                                        + ": còn "
                                        + row.getLong("stock")
                                        + ", ngưỡng "
                                        + row.getInt("low_stock_threshold")
                                        + "."));
    }

    public List<Alert> expiry(Long shopId, LocalDate today, boolean expired) {
        String condition =
                expired
                        ? "b.expiry_date < :today"
                        : "b.expiry_date >= :today AND DATEDIFF(DAY,:today,b.expiry_date)<=COALESCE(p.expiry_warning_days,3)";
        return jdbc.query(
                """
                SELECT b.batch_id,b.expiry_date,p.name,v.variant_name,b.quantity_on_hand
                FROM dbo.inventory_batches b JOIN dbo.product_variants v ON v.variant_id=b.variant_id
                JOIN dbo.products p ON p.product_id=v.product_id
                WHERE p.shop_id=:shop AND p.status<>'DELETED' AND v.status<>'DELETED'
                    AND b.status='ACTIVE' AND b.quantity_on_hand>0 AND
                """
                        + condition,
                new MapSqlParameterSource("shop", shopId).addValue("today", today),
                (row, index) ->
                        new Alert(
                                row.getLong("batch_id"),
                                expired ? "Lô hàng đã hết hạn" : "Lô hàng sắp hết hạn",
                                VietFreshHub.common.BusinessCodes.format(
                                                "LOT", row.getLong("batch_id"))
                                        + " · "
                                        + row.getString("name")
                                        + " · "
                                        + row.getString("variant_name")
                                        + ": hạn dùng "
                                        + row.getDate("expiry_date")
                                        + ", tồn thực tế "
                                        + row.getInt("quantity_on_hand")
                                        + "."));
    }

    public boolean belongsToShop(Long shopId, Long entityId, boolean batch) {
        String from =
                batch
                        ? "FROM dbo.inventory_batches b JOIN dbo.product_variants v ON v.variant_id=b.variant_id JOIN dbo.products p ON p.product_id=v.product_id WHERE b.batch_id=:entity"
                        : "FROM dbo.product_variants v JOIN dbo.products p ON p.product_id=v.product_id WHERE v.variant_id=:entity";
        return jdbc.queryForObject(
                        "SELECT COUNT(*) " + from + " AND p.shop_id=:shop",
                        new MapSqlParameterSource("shop", shopId).addValue("entity", entityId),
                        Long.class)
                > 0;
    }
}
