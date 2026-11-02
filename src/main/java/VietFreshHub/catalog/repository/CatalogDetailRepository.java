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
        return new Details(List.of(), 0, List.of());
    }
}
