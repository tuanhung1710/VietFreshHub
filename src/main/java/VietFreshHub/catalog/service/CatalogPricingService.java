package VietFreshHub.catalog.service;

import VietFreshHub.catalog.repository.CatalogOfferSql;
import VietFreshHub.inventory.rule.InventoryRules;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogPricingService {
    public record Offer(BigDecimal price, BigDecimal basePrice, int availableStock) { }
    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, Offer> offers(List<Long> variantIds) {
        if (variantIds.isEmpty()) return Map.of();
        var today = InventoryRules.today();
        var parameters = new MapSqlParameterSource("today", today).addValue("nearDate", today.plusDays(3))
                .addValue("ids", variantIds);
        Map<Long, Offer> result = new LinkedHashMap<>();
        jdbc.query(CatalogOfferSql.CTE + "SELECT * FROM variant_offers WHERE variant_id IN (:ids)", parameters,
                (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getLong("variant_id"),
                        new Offer(row.getBigDecimal("price"), row.getBigDecimal("base_price"),
                                (int) Math.min(Integer.MAX_VALUE, row.getLong("stock")))));
        return result;
    }
}
