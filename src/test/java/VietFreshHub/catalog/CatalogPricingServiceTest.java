package VietFreshHub.catalog;

import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.inventory.rule.InventoryRules;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogPricingServiceTest {
    private JdbcTemplate jdbc;
    private CatalogPricingService pricing;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:pricing" + System.nanoTime() + ";MODE=MSSQLServer");
        jdbc = new JdbcTemplate(source);
        // Keep the database alive for each connection used by JdbcTemplate.
        jdbc.execute("SET DB_CLOSE_DELAY -1");
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.product_variants(variant_id BIGINT, product_id BIGINT, status VARCHAR, price DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE dbo.inventory_batches(batch_id BIGINT, variant_id BIGINT, status VARCHAR, expiry_date DATE, quantity_on_hand INT, reserved_quantity INT)");
        jdbc.execute("CREATE TABLE dbo.batch_markdowns(batch_id BIGINT, status VARCHAR, start_date DATE, end_date DATE, price DECIMAL(18,2))");
        jdbc.update("INSERT INTO dbo.product_variants VALUES (1,1,'ACTIVE',50000),(2,1,'OUT_OF_STOCK',60000)");
        var today = InventoryRules.today();
        jdbc.update("INSERT INTO dbo.inventory_batches VALUES (1,1,'ACTIVE',?,8,3),(2,1,'ACTIVE',?,20,0)", today.plusDays(3), today.plusDays(10));
        jdbc.update("INSERT INTO dbo.batch_markdowns VALUES (1,'ACTIVE',?,?,30000),(1,'ACTIVE',?,?,35000)", today, today.plusDays(3), today, today.plusDays(3));
        pricing = new CatalogPricingService(new NamedParameterJdbcTemplate(source));
    }

    @Test
    void discountsOnlyCountStockAtTheLowestPriceWithoutDuplicatingBatches() {
        var offers = pricing.offers(List.of(1L, 2L));
        assertEquals(0, offers.get(1L).price().compareTo(new java.math.BigDecimal("30000")));
        assertEquals(5, offers.get(1L).availableStock());
        assertEquals(0, offers.get(2L).availableStock());
    }

    @Test
    void expiredOrCancelledMarkdownFallsBackToBasePriceAndFullStock() {
        jdbc.update("UPDATE dbo.batch_markdowns SET status='CANCELLED'");
        var offer = pricing.offers(List.of(1L)).get(1L);
        assertEquals(0, offer.price().compareTo(offer.basePrice()));
        assertEquals(25, offer.availableStock());
        jdbc.update("UPDATE dbo.inventory_batches SET expiry_date=?", InventoryRules.today().minusDays(1));
        assertEquals(0, pricing.offers(List.of(1L)).get(1L).availableStock());
    }
}
