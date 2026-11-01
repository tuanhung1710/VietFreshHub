package VietFreshHub.shop.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class ShopOfferRepository {
    public record Offer(String name, String description, String code, String discountType,
                        BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minimumOrderValue,
                        Integer perUserLimit, LocalDateTime endAt) { }
    private final NamedParameterJdbcTemplate jdbc;
    private static final String OFFERS = """
        WITH offers AS (
            SELECT p.promotion_id AS id, 'PROMOTION' AS kind, p.name, p.description, CAST(NULL AS VARCHAR(100)) AS code,
                p.discount_type, p.discount_value, p.max_discount, p.minimum_order_value,
                CAST(NULL AS INT) AS per_user_limit, p.end_at
            FROM dbo.promotions p WHERE p.shop_id=:shop AND p.status='ACTIVE' AND p.start_at<=:now AND p.end_at>:now
            UNION ALL
            SELECT c.coupon_id, 'COUPON', p.name, p.description, c.code,
                p.discount_type, p.discount_value, p.max_discount,
                CASE WHEN c.minimum_order_value>p.minimum_order_value THEN c.minimum_order_value ELSE p.minimum_order_value END,
                c.per_user_limit, CASE WHEN c.end_at<p.end_at THEN c.end_at ELSE p.end_at END
            FROM dbo.coupons c JOIN dbo.promotions p ON p.promotion_id=c.promotion_id
            WHERE p.shop_id=:shop AND p.status='ACTIVE' AND p.start_at<=:now AND p.end_at>:now
                AND c.status='ACTIVE' AND c.start_at<=:now AND c.end_at>:now
                AND (c.usage_limit IS NULL OR (SELECT COUNT(*) FROM dbo.coupon_usages u WHERE u.coupon_id=c.coupon_id)<c.usage_limit)
        )
        """;

    public Page<Offer> list(Long shopId, int page) {
        var pageable = PageRequest.of(page - 1, 12);
        var parameters = new MapSqlParameterSource("shop", shopId)
                .addValue("now", VietFreshHub.inventory.rule.InventoryRules.now()).addValue("offset", pageable.getOffset());
        Long count = jdbc.queryForObject(OFFERS + "SELECT COUNT(*) FROM offers", parameters, Long.class);
        var rows = jdbc.query(OFFERS + "SELECT * FROM offers ORDER BY end_at,kind,id OFFSET :offset ROWS FETCH NEXT 12 ROWS ONLY",
                parameters, (row, index) -> new Offer(row.getString("name"), row.getString("description"), row.getString("code"),
                        row.getString("discount_type"), row.getBigDecimal("discount_value"), row.getBigDecimal("max_discount"),
                        row.getBigDecimal("minimum_order_value"), (Integer) row.getObject("per_user_limit"),
                        row.getTimestamp("end_at").toLocalDateTime().atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(VietFreshHub.inventory.rule.InventoryRules.SHOP_ZONE).toLocalDateTime()));
        return new PageImpl<>(rows, pageable, count == null ? 0 : count);
    }
}
