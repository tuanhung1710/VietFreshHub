package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.dto.CatalogOffer;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CatalogOfferRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** Giữ chung CTE tính giá với bộ lọc catalog để giá hiển thị và giỏ hàng khớp nhau. */
    public Map<Long, CatalogOffer> findOffers(List<Long> variantIds, LocalDate today) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        var parameters =
                new MapSqlParameterSource("today", today)
                        .addValue("nearDate", today.plusDays(3))
                        .addValue("ids", variantIds);
        Map<Long, CatalogOffer> result = new LinkedHashMap<>();
        jdbc.query(
                CatalogOfferSql.CTE
                        + "SELECT variant_id,price,base_price,stock FROM variant_offers WHERE variant_id IN (:ids)",
                parameters,
                (RowCallbackHandler)
                        row ->
                                result.put(
                                        row.getLong("variant_id"),
                                        new CatalogOffer(
                                                row.getBigDecimal("price"),
                                                row.getBigDecimal("base_price"),
                                                (int)
                                                        Math.min(
                                                                Integer.MAX_VALUE,
                                                                row.getLong("stock")))));
        return result;
    }
}
