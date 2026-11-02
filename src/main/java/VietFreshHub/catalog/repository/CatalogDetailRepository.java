package VietFreshHub.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CatalogDetailRepository {
    public record Review(String reviewer, int rating, String content, LocalDateTime createdAt) { }
    public record Details(List<String> images, long reviewCount, List<Review> reviews) { }
    private final JdbcTemplate jdbc;

    public Details find(Long productId) {
        var images = jdbc.query("""
                SELECT image_url FROM dbo.product_images WHERE product_id=? AND variant_id IS NULL
                ORDER BY is_primary DESC,sort_order,image_id
                """, (row, index) -> row.getString("image_url"), productId);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.reviews WHERE product_id=? AND status='APPROVED'", Long.class, productId);
        var reviews = jdbc.query("""
                SELECT TOP (5) u.full_name,r.rating,r.content,r.created_at
                FROM dbo.reviews r JOIN dbo.users u ON u.user_id=r.user_id
                WHERE r.product_id=? AND r.status='APPROVED'
                ORDER BY r.created_at DESC,r.review_id DESC
                """, (row, index) -> new Review(row.getString("full_name"), row.getInt("rating"),
                row.getString("content"), row.getTimestamp("created_at").toLocalDateTime()
                        .atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime()), productId);
        return new Details(images, count, reviews);
    }
}
