package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CatalogBrowseRepository {
    public static final int PAGE_SIZE = 12;
    private final NamedParameterJdbcTemplate jdbc;

    public Page<Long> search(CatalogBrowseFilter filter, Set<Long> categoryIds) {
        var page = PageRequest.of(filter.getPage() - 1, PAGE_SIZE);
        if (categoryIds.isEmpty()) return Page.empty(page);
        var parameters = new MapSqlParameterSource("categories", categoryIds)
                .addValue("keyword", "%" + escapeLike(filter.getKeyword().strip().toLowerCase(java.util.Locale.ROOT)) + "%")
                .addValue("offset", page.getOffset()).addValue("size", PAGE_SIZE);
        String from = """
                FROM dbo.products p JOIN dbo.shops s ON s.shop_id = p.shop_id
                WHERE p.status = 'ACTIVE' AND p.moderation_blocked = 0 AND s.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM dbo.product_categories c WHERE c.product_id = p.product_id AND c.category_id IN (:categories))
                  AND EXISTS (SELECT 1 FROM dbo.product_variants v WHERE v.product_id = p.product_id AND v.status IN ('ACTIVE','OUT_OF_STOCK'))
                  AND LOWER(p.name) LIKE :keyword ESCAPE '!'
                """;
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + from, parameters, Long.class);
        var ids = jdbc.query("SELECT p.product_id " + from + " ORDER BY p.product_id DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                parameters, (row, index) -> row.getLong(1));
        return new PageImpl<>(ids, page, count == null ? 0 : count);
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
    }
}
