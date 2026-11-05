package VietFreshHub.shop.repository;

import VietFreshHub.common.VietnamDatabaseTime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import VietFreshHub.common.PageSizes;

@Repository
@RequiredArgsConstructor
public class ShopFavoriteRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public boolean exists(Long userId, Long shopId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.favorite_shops WHERE user_id=:user AND shop_id=:shop",
                parameters(userId, shopId), Long.class) > 0;
    }

    public void add(Long userId, Long shopId) {
        jdbc.update("INSERT INTO dbo.favorite_shops(user_id,shop_id,created_at) VALUES(:user,:shop,:now)",
                parameters(userId, shopId).addValue("now",
                        VietnamDatabaseTime.toDatabase(VietFreshHub.inventory.rule.InventoryRules.now())));
    }

    public void remove(Long userId, Long shopId) {
        jdbc.update("DELETE FROM dbo.favorite_shops WHERE user_id=:user AND shop_id=:shop", parameters(userId, shopId));
    }

    public Page<Long> list(Long userId, int page) {
        var pageable = PageRequest.of(page - 1, PageSizes.DEFAULT);
        var parameters = new MapSqlParameterSource("user", userId).addValue("offset", pageable.getOffset()).addValue("size", pageable.getPageSize());
        String from = " FROM dbo.favorite_shops f JOIN dbo.shops s ON s.shop_id=f.shop_id WHERE f.user_id=:user AND s.status='ACTIVE' AND s.availability_status='OPEN'";
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + from, parameters, Long.class);
        var ids = jdbc.query("SELECT f.shop_id" + from + " ORDER BY f.created_at DESC,f.shop_id DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                parameters, (row, index) -> row.getLong(1));
        return new PageImpl<>(ids, pageable, count == null ? 0 : count);
    }

    private MapSqlParameterSource parameters(Long userId, Long shopId) {
        return new MapSqlParameterSource("user", userId).addValue("shop", shopId);
    }
}
