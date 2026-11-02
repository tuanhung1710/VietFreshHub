package VietFreshHub.catalog;

import VietFreshHub.catalog.repository.StorefrontShopRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

class StorefrontShopAddressTest {
    private JdbcTemplate jdbc;
    private StorefrontShopRepository shops;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:shop-address-" + System.nanoTime() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.shops(shop_id BIGINT,status VARCHAR,availability_status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.shop_members(shop_id BIGINT,user_id BIGINT,member_role VARCHAR,status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.addresses(address_id BIGINT,user_id BIGINT,is_default BIT,formatted_address VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.shop_locations(shop_id BIGINT,formatted_address VARCHAR,address_line VARCHAR,ward VARCHAR,district VARCHAR,province VARCHAR)");
        jdbc.update("INSERT INTO dbo.shops VALUES(7,'ACTIVE','OPEN'),(8,'ACTIVE','OPEN')");
        jdbc.update("INSERT INTO dbo.shop_members VALUES(7,2,'OWNER','ACTIVE'),(8,3,'OWNER','ACTIVE'),(7,4,'OWNER','INACTIVE')");
        shops = new StorefrontShopRepository(jdbc);
    }

    @Test
    void usesThisShopsLocationInsteadOfTheOwnersPersonalAddress() {
        jdbc.update("INSERT INTO dbo.addresses VALUES(1,2,0,'Địa chỉ phụ'),(2,2,1,'Địa chỉ mặc định của chủ shop'),(3,3,1,'Shop khác'),(4,7,1,'User có ID trùng shop'),(5,4,1,'Chủ shop không còn hoạt động')");
        jdbc.update("INSERT INTO dbo.shop_locations VALUES(7,'Địa chỉ đầy đủ của shop 7','Đường 7','Phường 7',NULL,'TP HCM'),(8,'Shop khác','Đường 8','Phường 8',NULL,'TP HCM')");
        assertEquals("Địa chỉ đầy đủ của shop 7", shops.formattedAddress(7L).orElseThrow());
    }

    @Test
    void buildsTheAddressFromThisShopsStoredComponentsWhenTheFormattedAddressIsBlank() {
        jdbc.update("INSERT INTO dbo.addresses VALUES(1,2,1,'   '),(3,2,0,'Địa chỉ thứ hai'),(2,2,0,'Địa chỉ đầu tiên')");
        jdbc.update("INSERT INTO dbo.shop_locations VALUES(7,'  ',' 126 Lũy Bán Bích ','Phường Tân Phú',NULL,'TP Hồ Chí Minh')");
        assertEquals("126 Lũy Bán Bích, Phường Tân Phú, TP Hồ Chí Minh", shops.formattedAddress(7L).orElseThrow());
    }

    @Test
    void missingShopLocationsNeverFallBackToUserAddressesAndClosedShopsStayUnavailable() {
        jdbc.update("INSERT INTO dbo.addresses VALUES(1,3,1,'Shop khác')");
        assertTrue(shops.formattedAddress(7L).isEmpty());
        jdbc.update("INSERT INTO dbo.shop_locations VALUES(8,'Địa chỉ shop 8','Đường 8','Phường 8',NULL,'TP HCM')");
        jdbc.update("UPDATE dbo.shops SET availability_status='CLOSED' WHERE shop_id=8");
        assertTrue(shops.formattedAddress(8L).isEmpty());
    }

    @Test
    void sellerAndBuyerDisplayTheSameCompleteShopAddress() {
        var location = new VietFreshHub.shop.entity.ShopLocation(7L);
        location.updateLocation(null, "TP Hồ Chí Minh", null, null, null, "Phường Tân Phú",
                "126 Lũy Bán Bích", null, null, null);
        assertEquals("126 Lũy Bán Bích, Phường Tân Phú, TP Hồ Chí Minh", location.getDisplayAddress());
    }
}
