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
    private static final String OFFERS = CatalogOfferSql.CTE + """
            , product_offers AS (
                SELECT p.product_id,
                    COALESCE(MIN(CASE WHEN v.status='ACTIVE' AND v.stock > 0 AND s.availability_status='OPEN' THEN v.price END), MIN(v.price)) AS price,
                    MAX(CASE WHEN v.status='ACTIVE' AND v.stock > 0 AND s.availability_status='OPEN' THEN 1 ELSE 0 END) AS available
                FROM dbo.products p JOIN dbo.shops s ON s.shop_id=p.shop_id
                JOIN variant_offers v ON v.product_id=p.product_id AND v.status IN ('ACTIVE','OUT_OF_STOCK')
                GROUP BY p.product_id
            )
            """;

    public Page<Long> search(CatalogBrowseFilter filter, Set<Long> categoryIds) {
        var page = PageRequest.of(filter.getPage() - 1, PAGE_SIZE);
        if (categoryIds.isEmpty()) return Page.empty(page);
        var parameters = new MapSqlParameterSource("categories", categoryIds)
                .addValue("today", VietFreshHub.inventory.rule.InventoryRules.today())
                .addValue("nearDate", VietFreshHub.inventory.rule.InventoryRules.today().plusDays(3))
                .addValue("keyword", "%" + escapeLike(filter.getKeyword().strip().toLowerCase(java.util.Locale.ROOT)) + "%")
                .addValue("offset", page.getOffset()).addValue("size", PAGE_SIZE);
        String from = """
                FROM dbo.products p JOIN dbo.shops s ON s.shop_id = p.shop_id JOIN product_offers offer ON offer.product_id=p.product_id
                WHERE p.status = 'ACTIVE' AND p.moderation_blocked = 0 AND s.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM dbo.product_categories c WHERE c.product_id = p.product_id AND c.category_id IN (:categories))
                  AND EXISTS (SELECT 1 FROM dbo.product_variants v WHERE v.product_id = p.product_id AND v.status IN ('ACTIVE','OUT_OF_STOCK'))
                  AND LOWER(p.name) LIKE :keyword ESCAPE '!'
                """;
        if (filter.getMinPrice() != null) {
            from += " AND offer.price >= :minPrice";
            parameters.addValue("minPrice", filter.getMinPrice());
        }
        if (filter.getMaxPrice() != null) {
            from += " AND offer.price <= :maxPrice";
            parameters.addValue("maxPrice", filter.getMaxPrice());
        }
        if ("available".equals(filter.getAvailability())) from += " AND offer.available = 1";
        if ("unavailable".equals(filter.getAvailability())) from += " AND offer.available = 0";
        Long count = jdbc.queryForObject(OFFERS + "SELECT COUNT(*) " + from, parameters, Long.class);
        String order = switch (filter.getSort()) {
            case "price_asc" -> "offer.price ASC, p.product_id DESC";
            case "price_desc" -> "offer.price DESC, p.product_id DESC";
            default -> "p.product_id DESC";
        };
        var ids = jdbc.query(OFFERS + "SELECT p.product_id " + from + " ORDER BY " + order + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                parameters, (row, index) -> row.getLong(1));
        return new PageImpl<>(ids, page, count == null ? 0 : count);
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
    }
}
