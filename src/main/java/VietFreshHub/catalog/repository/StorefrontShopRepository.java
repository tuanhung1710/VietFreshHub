package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.dto.StorefrontShopOption;
import VietFreshHub.shop.util.ShopAddressFormatter;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StorefrontShopRepository {
    private final JdbcTemplate jdbc;

    public Optional<String> formattedAddress(Long shopId) {
        return jdbc
                .query(
                        """
                SELECT location.formatted_address,location.address_line,location.ward,location.district,location.province
                FROM dbo.shops s
                JOIN dbo.shop_locations location ON location.shop_id=s.shop_id
                WHERE s.shop_id=? AND s.status='ACTIVE' AND s.availability_status='OPEN'
                """,
                        (row, index) ->
                                ShopAddressFormatter.format(
                                        row.getString("formatted_address"),
                                        row.getString("address_line"),
                                        row.getString("ward"),
                                        row.getString("district"),
                                        row.getString("province")),
                        shopId)
                .stream()
                .filter(address -> address != null && !address.isBlank())
                .findFirst();
    }

    public List<StorefrontShopOption> list() {
        return jdbc.query(
                """
                SELECT s.shop_id,s.shop_name,s.description,s.banner_url,location.province
                FROM dbo.shops s LEFT JOIN dbo.shop_locations location ON location.shop_id=s.shop_id
                WHERE s.status='ACTIVE' AND s.availability_status='OPEN' ORDER BY s.shop_id
                """,
                (row, index) ->
                        new StorefrontShopOption(
                                row.getLong("shop_id"),
                                row.getString("shop_name"),
                                row.getString("province"),
                                row.getString("description"),
                                row.getString("banner_url")));
    }
}
