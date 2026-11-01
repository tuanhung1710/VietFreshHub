package VietFreshHub.shop;

import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.shop.repository.ShopFavoriteRepository;
import VietFreshHub.shop.repository.ShopOfferRepository;
import VietFreshHub.shop.repository.ShopReviewRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class ShopReadRepositoriesTest {
    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:shop-reads" + System.nanoTime() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.users(user_id BIGINT,full_name VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.shops(shop_id BIGINT,status VARCHAR,availability_status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.reviews(review_id BIGINT,user_id BIGINT,shop_id BIGINT,rating INT,content VARCHAR,status VARCHAR,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE dbo.favorite_shops(user_id BIGINT,shop_id BIGINT,created_at TIMESTAMP,PRIMARY KEY(user_id,shop_id))");
        jdbc.execute("CREATE TABLE dbo.promotions(promotion_id BIGINT,shop_id BIGINT,name VARCHAR,description VARCHAR,discount_type VARCHAR,discount_value DECIMAL,max_discount DECIMAL,minimum_order_value DECIMAL,start_at TIMESTAMP,end_at TIMESTAMP,status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.coupons(coupon_id BIGINT,promotion_id BIGINT,code VARCHAR,minimum_order_value DECIMAL,per_user_limit INT,usage_limit INT,start_at TIMESTAMP,end_at TIMESTAMP,status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.coupon_usages(coupon_id BIGINT)");
    }

    @Test
    void shopReviewsOnlyPublishApprovedRatingsForTheRequestedShop() {
        jdbc.update("INSERT INTO dbo.users VALUES(1,'Minh Anh')");
        jdbc.update("INSERT INTO dbo.reviews VALUES(1,1,7,5,'Tươi','APPROVED',CURRENT_TIMESTAMP),(2,1,7,1,'Ẩn','PENDING',CURRENT_TIMESTAMP),(3,1,8,1,'Khác','APPROVED',CURRENT_TIMESTAMP)");
        var repository = new ShopReviewRepository(named);
        assertEquals(1, repository.summary(7L).count());
        assertEquals(5, repository.summary(7L).average().intValue());
        assertEquals("Tươi", repository.list(7L, 5, 1).getContent().getFirst().content());
        assertTrue(repository.list(7L, 1, 1).isEmpty());
    }

    @Test
    void offersRespectBothTimeWindowsAndCouponUsageLimit() {
        var now = InventoryRules.now();
        jdbc.update("INSERT INTO dbo.promotions VALUES(1,7,'Ưu đãi','Mô tả','PERCENT',10,50000,100000,?,?,'ACTIVE')", now.minusHours(1), now.plusHours(1));
        jdbc.update("INSERT INTO dbo.coupons VALUES(1,1,'SON10',200000,1,1,?,?,'ACTIVE')", now.minusHours(1), now.plusHours(1));
        var repository = new ShopOfferRepository(named);
        assertEquals(2, repository.list(7L, 1).getTotalElements());
        var coupon = repository.list(7L, 1).stream().filter(o -> o.code() != null).findFirst().orElseThrow();
        assertEquals(200000, coupon.minimumOrderValue().intValue());
        jdbc.update("INSERT INTO dbo.coupon_usages VALUES(1)");
        assertEquals(1, repository.list(7L, 1).getTotalElements());
        jdbc.update("UPDATE dbo.promotions SET end_at=?", now.minusSeconds(1));
        assertTrue(repository.list(7L, 1).isEmpty());
    }

    @Test
    void favoriteRemovalOnlyChangesTheCurrentCustomer() {
        jdbc.update("INSERT INTO dbo.shops VALUES(7,'ACTIVE','OPEN')");
        var repository = new ShopFavoriteRepository(named);
        repository.add(1L, 7L);
        repository.add(2L, 7L);
        repository.remove(1L, 7L);
        assertFalse(repository.exists(1L, 7L));
        assertTrue(repository.exists(2L, 7L));
        assertEquals(1, repository.list(2L, 1).getTotalElements());
    }
}
