package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InventoryAlertRepositoryTest {
    private JdbcTemplate jdbc;
    private InventoryAlertRepository repository;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:alerts" + System.nanoTime() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.products(product_id BIGINT,shop_id BIGINT,name VARCHAR,status VARCHAR,expiry_warning_days INT)");
        jdbc.execute("CREATE TABLE dbo.product_variants(variant_id BIGINT,product_id BIGINT,variant_name VARCHAR,status VARCHAR,low_stock_threshold INT)");
        jdbc.execute("CREATE TABLE dbo.inventory_batches(batch_id BIGINT,variant_id BIGINT,expiry_date DATE,status VARCHAR,quantity_on_hand INT,reserved_quantity INT)");
        jdbc.execute("CREATE TABLE dbo.inventory_alert_states(shop_id BIGINT,alert_type VARCHAR,entity_id BIGINT,active BIT,updated_at TIMESTAMP,PRIMARY KEY(shop_id,alert_type,entity_id))");
        jdbc.update("INSERT INTO dbo.products VALUES(1,7,'Xoài','ACTIVE',3)");
        jdbc.update("INSERT INTO dbo.product_variants VALUES(1,1,'Hộp','ACTIVE',2)");
        jdbc.update("INSERT INTO dbo.inventory_batches VALUES(1,1,?,'ACTIVE',5,3),(2,1,?,'ACTIVE',20,0)",
                InventoryRules.today(), InventoryRules.today().minusDays(1));
        repository = new InventoryAlertRepository(new NamedParameterJdbcTemplate(source));
    }

    @Test
    void stockCountsExcludeExpiredAndReservedGoodsAndExpiryIncludesToday() {
        assertEquals(1, repository.lowStock(7L, InventoryRules.today()).size());
        assertEquals(1L, repository.expiry(7L, InventoryRules.today(), false).getFirst().entityId());
        assertEquals(2L, repository.expiry(7L, InventoryRules.today(), true).getFirst().entityId());
        jdbc.update("UPDATE dbo.inventory_batches SET expiry_date=? WHERE batch_id=1", InventoryRules.today().plusDays(4));
        assertTrue(repository.expiry(7L, InventoryRules.today(), false).isEmpty());
        assertFalse(repository.belongsToShop(8L, 1L, true));
    }

    @Test
    void persistedAlertStateCanBeResolvedAndReactivated() {
        repository.setActive(7L, "LOW_STOCK", 1L, true);
        assertEquals(Set.of(1L), repository.activeIds(7L, "LOW_STOCK"));
        repository.setActive(7L, "LOW_STOCK", 1L, false);
        assertTrue(repository.activeIds(7L, "LOW_STOCK").isEmpty());
        repository.setActive(7L, "LOW_STOCK", 1L, true);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.inventory_alert_states", Integer.class));
    }
}
