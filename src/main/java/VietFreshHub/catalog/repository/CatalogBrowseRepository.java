package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.common.Pagination;
import VietFreshHub.inventory.rule.InventoryRules;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CatalogBrowseRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String METRICS =
            """
            product_ratings AS (
                SELECT product_id, AVG(CAST(rating AS DECIMAL(10,4))) AS rating
                FROM dbo.reviews WHERE status='APPROVED' AND product_id IS NOT NULL GROUP BY product_id
            ), product_sales AS (
                SELECT v.product_id, SUM(CAST(i.quantity AS BIGINT)) AS sales
                FROM dbo.order_items i JOIN dbo.product_variants v ON v.variant_id=i.variant_id
                JOIN dbo.shop_orders so ON so.shop_order_id=i.shop_order_id
                WHERE so.status='COMPLETED' GROUP BY v.product_id
            )
            """;
    private static final String OFFERS =
            CatalogOfferSql.CTE
                    + """
            , product_offers AS (
                SELECT p.product_id,
                    COALESCE(MIN(CASE WHEN v.status='ACTIVE' AND v.stock > 0 AND s.availability_status='OPEN' THEN v.price END), MIN(v.price)) AS price,
                    MAX(CASE WHEN v.status='ACTIVE' AND v.stock > 0 AND s.availability_status='OPEN' THEN 1 ELSE 0 END) AS available
                FROM dbo.products p JOIN dbo.shops s ON s.shop_id=p.shop_id
                JOIN variant_offers v ON v.product_id=p.product_id AND v.status IN ('ACTIVE','OUT_OF_STOCK')
                GROUP BY p.product_id
            )
            """
                    + ", "
                    + METRICS;

    public record ProductMetrics(BigDecimal rating, long sales, String place) {}

    public Page<Long> search(CatalogBrowseFilter filter, Set<Long> categoryIds) {
        var page = Pagination.request(filter.getPage(), filter.getPageSize());
        if (categoryIds.isEmpty()) return Page.empty(page);
        var today = InventoryRules.today();
        var parameters =
                new MapSqlParameterSource("categories", categoryIds)
                        .addValue("today", today)
                        .addValue("nearDate", today.plusDays(3))
                        .addValue(
                                "keyword",
                                "%"
                                        + escapeLike(
                                                filter.getKeyword()
                                                        .strip()
                                                        .toLowerCase(java.util.Locale.ROOT))
                                        + "%")
                        .addValue("minRating", filter.getMinRating())
                        .addValue("size", filter.getPageSize());
        String from =
                """
                FROM dbo.products p JOIN dbo.shops s ON s.shop_id = p.shop_id JOIN product_offers offer ON offer.product_id=p.product_id
                LEFT JOIN dbo.shop_locations location ON location.shop_id=s.shop_id
                LEFT JOIN product_ratings rating ON rating.product_id=p.product_id
                LEFT JOIN product_sales sales ON sales.product_id=p.product_id
                WHERE p.status = 'ACTIVE' AND p.moderation_blocked = 0 AND s.status = 'ACTIVE' AND s.availability_status='OPEN' AND offer.available=1
                  AND EXISTS (SELECT 1 FROM dbo.product_categories c WHERE c.product_id = p.product_id AND c.category_id IN (:categories))
                  AND (LOWER(p.name) LIKE :keyword ESCAPE '!' OR LOWER(s.shop_name) LIKE :keyword ESCAPE '!'
                       OR EXISTS (SELECT 1 FROM dbo.product_categories pc JOIN dbo.categories c ON c.category_id=pc.category_id
                                  WHERE pc.product_id=p.product_id AND c.category_id IN (:categories) AND LOWER(c.name) LIKE :keyword ESCAPE '!'))
                  AND COALESCE(rating.rating,0)>=:minRating
                """;
        if (filter.getMinPrice() != null) {
            from += " AND offer.price >= :minPrice";
            parameters.addValue("minPrice", filter.getMinPrice());
        }
        if (filter.getShopId() != null) {
            from += " AND p.shop_id=:shopId";
            parameters.addValue("shopId", filter.getShopId());
        }
        if (filter.getShopIds() != null && !filter.getShopIds().isEmpty()) {
            from += " AND p.shop_id IN (:shops)";
            parameters.addValue("shops", filter.getShopIds());
        }
        if (filter.getPlaces() != null && !filter.getPlaces().isEmpty()) {
            from += " AND location.province IN (:places)";
            parameters.addValue("places", filter.getPlaces());
        }
        if (filter.getMaxPrice() != null) {
            from += " AND offer.price <= :maxPrice";
            parameters.addValue("maxPrice", filter.getMaxPrice());
        }
        Long count =
                jdbc.queryForObject(OFFERS + "SELECT COUNT(*) " + from, parameters, Long.class);
        long total = count == null ? 0 : count;
        page = Pagination.clamp(filter.getPage(), filter.getPageSize(), total);
        parameters.addValue("offset", page.getOffset());
        String order =
                switch (filter.getSort()) {
                    case "asc", "price_asc" -> "offer.price ASC, p.product_id ASC";
                    case "desc", "price_desc" -> "offer.price DESC, p.product_id ASC";
                    case "rating" -> "COALESCE(rating.rating,0) DESC, p.product_id ASC";
                    case "sales", "best_selling" ->
                            "COALESCE(sales.sales,0) DESC, p.product_id ASC";
                    case "latest", "newest" -> "p.product_id DESC";
                    default -> "p.product_id ASC";
                };
        var ids =
                jdbc.query(
                        OFFERS
                                + "SELECT p.product_id "
                                + from
                                + " ORDER BY "
                                + order
                                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                        parameters,
                        (row, index) -> row.getLong(1));
        return new PageImpl<>(ids, page, total);
    }

    public Map<Long, ProductMetrics> metrics(List<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<Long, ProductMetrics> result = new HashMap<>();
        jdbc.query(
                "WITH "
                        + METRICS
                        + """
                SELECT p.product_id, COALESCE(r.rating,0) AS rating, COALESCE(s.sales,0) AS sales, location.province
                FROM dbo.products p LEFT JOIN product_ratings r ON r.product_id=p.product_id
                LEFT JOIN product_sales s ON s.product_id=p.product_id
                LEFT JOIN dbo.shop_locations location ON location.shop_id=p.shop_id
                WHERE p.product_id IN (:ids)
                """,
                new MapSqlParameterSource("ids", productIds),
                (RowCallbackHandler)
                        row ->
                                result.put(
                                        row.getLong("product_id"),
                                        new ProductMetrics(
                                                row.getBigDecimal("rating"),
                                                row.getLong("sales"),
                                                row.getString("province"))));
        return result;
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
    }
}
