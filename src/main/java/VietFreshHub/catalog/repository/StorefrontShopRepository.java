package VietFreshHub.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StorefrontShopRepository {
    public record ShopOption(Long id, String name, String place, String description) { }
    private final JdbcTemplate jdbc;

    public List<ShopOption> list() {
        return jdbc.query("""
                SELECT s.shop_id,s.shop_name,s.description,location.province
                FROM dbo.shops s LEFT JOIN dbo.shop_locations location ON location.shop_id=s.shop_id
                WHERE s.status='ACTIVE' AND s.availability_status='OPEN' ORDER BY s.shop_id
                """, (row, index) -> new ShopOption(row.getLong("shop_id"), row.getString("shop_name"),
                row.getString("province"), row.getString("description")));
    }
}
