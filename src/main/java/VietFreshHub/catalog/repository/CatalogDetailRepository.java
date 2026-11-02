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
        return new Details(images, 0, List.of());
    }
}
