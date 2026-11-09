package VietFreshHub.shop.repository;

import VietFreshHub.common.VietnamDatabaseTime;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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

    private MapSqlParameterSource parameters(Long userId, Long shopId) {
        return new MapSqlParameterSource("user", userId).addValue("shop", shopId);
    }
}
