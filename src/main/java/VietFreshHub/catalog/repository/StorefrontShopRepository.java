package VietFreshHub.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StorefrontShopRepository {
    public record ShopOption(Long id, String name, String place, String description) { }
    private final JdbcTemplate jdbc;

    public Optional<String> formattedAddress(Long shopId) {
        return jdbc.query("""
                SELECT TOP (1) a.formatted_address
                FROM dbo.shops s
                JOIN dbo.shop_members m ON m.shop_id=s.shop_id
                    AND m.member_role='OWNER' AND m.status='ACTIVE'
                JOIN dbo.addresses a ON a.user_id=m.user_id
                WHERE s.shop_id=? AND s.status='ACTIVE' AND s.availability_status='OPEN'
                  AND NULLIF(LTRIM(RTRIM(a.formatted_address)),'') IS NOT NULL
                ORDER BY a.is_default DESC,a.address_id
                """, (row, index) -> row.getString("formatted_address"), shopId).stream().findFirst();
    }

    public List<ShopOption> list() {
        return jdbc.query("""
                SELECT s.shop_id,s.shop_name,s.description,location.province
                FROM dbo.shops s LEFT JOIN dbo.shop_locations location ON location.shop_id=s.shop_id
                WHERE s.status='ACTIVE' AND s.availability_status='OPEN' ORDER BY s.shop_id
                """, (row, index) -> new ShopOption(row.getLong("shop_id"), row.getString("shop_name"),
                row.getString("province"), row.getString("description")));
    }
}
