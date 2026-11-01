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
import VietFreshHub.common.PageSizes;

@Repository
@RequiredArgsConstructor
public class ShopReviewRepository {
    public record Review(int rating, String content, String customerName, LocalDateTime createdAt) { }
    public record Summary(long count, BigDecimal average) { }
    private final NamedParameterJdbcTemplate jdbc;

    public Summary summary(Long shopId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total, COALESCE(AVG(CAST(rating AS DECIMAL(10,4))),0) AS average
                FROM dbo.reviews WHERE shop_id=:shop AND status='APPROVED'
                """, new MapSqlParameterSource("shop", shopId),
                (row, index) -> new Summary(row.getLong("total"), row.getBigDecimal("average")));
    }

    public Page<Review> list(Long shopId, Integer rating, int page) {
        var pageable = PageRequest.of(page - 1, PageSizes.DEFAULT);
        var parameters = new MapSqlParameterSource("shop", shopId).addValue("rating", rating)
                .addValue("offset", pageable.getOffset()).addValue("size", pageable.getPageSize());
        String where = " FROM dbo.reviews r JOIN dbo.users u ON u.user_id=r.user_id WHERE r.shop_id=:shop AND r.status='APPROVED'";
        if (rating != null) where += " AND r.rating=:rating";
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + where, parameters, Long.class);
        var rows = jdbc.query("SELECT r.rating,r.content,u.full_name,r.created_at" + where
                        + " ORDER BY r.created_at DESC,r.review_id DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                parameters, (row, index) -> new Review(row.getInt("rating"), row.getString("content"),
                        row.getString("full_name"), row.getTimestamp("created_at").toLocalDateTime()
                                .atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(VietFreshHub.inventory.rule.InventoryRules.SHOP_ZONE).toLocalDateTime()));
        return new PageImpl<>(rows, pageable, count == null ? 0 : count);
    }
}
