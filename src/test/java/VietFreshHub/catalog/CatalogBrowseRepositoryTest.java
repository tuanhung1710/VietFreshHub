package VietFreshHub.catalog;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.repository.CatalogBrowseRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CatalogBrowseRepositoryTest {
    private JdbcTemplate jdbc;
    private CatalogBrowseRepository repository;
    private final CatalogBrowseFilter filter = new CatalogBrowseFilter();

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:browse" + System.nanoTime() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.products(product_id BIGINT,shop_id BIGINT,name VARCHAR,status VARCHAR,moderation_blocked INT)");
        jdbc.execute("CREATE TABLE dbo.shops(shop_id BIGINT,status VARCHAR,availability_status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.product_categories(product_id BIGINT,category_id BIGINT)");
        jdbc.execute("CREATE TABLE dbo.product_variants(variant_id BIGINT,product_id BIGINT,status VARCHAR,price DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE dbo.inventory_batches(batch_id BIGINT,variant_id BIGINT,status VARCHAR,expiry_date DATE,quantity_on_hand INT,reserved_quantity INT)");
        jdbc.execute("CREATE TABLE dbo.batch_markdowns(batch_id BIGINT,status VARCHAR,start_date DATE,end_date DATE,price DECIMAL(18,2))");
        jdbc.update("INSERT INTO dbo.shops VALUES(1,'ACTIVE','OPEN'),(2,'ACTIVE','CLOSED')");
        jdbc.update("INSERT INTO dbo.products VALUES(1,1,'Xoài','ACTIVE',0),(2,2,'Cam','ACTIVE',0),(3,1,'Hidden','ACTIVE',1)");
        jdbc.update("INSERT INTO dbo.product_categories VALUES(1,7),(2,7),(3,7)");
        jdbc.update("INSERT INTO dbo.product_variants VALUES(1,1,'ACTIVE',50000),(2,2,'ACTIVE',20000),(3,3,'ACTIVE',10000)");
        jdbc.update("INSERT INTO dbo.inventory_batches VALUES(1,1,'ACTIVE',?,10,0),(2,2,'ACTIVE',?,10,0)", InventoryRules.today(), InventoryRules.today());
        jdbc.update("INSERT INTO dbo.batch_markdowns VALUES(1,'ACTIVE',?,?,15000)", InventoryRules.today(), InventoryRules.today());
        repository = new CatalogBrowseRepository(new NamedParameterJdbcTemplate(source));
    }

    @Test
    void priceUsesCurrentMarkdownAndModerationRemainsEnforced() {
        filter.setMaxPrice(new BigDecimal("16000"));
        var result = repository.search(filter, Set.of(7L));
        assertEquals(List.of(1L), result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void closedShopsAreVisibleButNotAvailableForPurchase() {
        filter.setAvailability("available");
        assertEquals(List.of(1L), repository.search(filter, Set.of(7L)).getContent());
        filter.setAvailability("unavailable");
        assertEquals(List.of(2L), repository.search(filter, Set.of(7L)).getContent());
    }

    @Test
    void searchEscapesSqlWildcardsAndRejectsInactiveCategory() {
        filter.setKeyword("%");
        assertTrue(repository.search(filter, Set.of(7L)).isEmpty());
        filter.setKeyword("");
        assertTrue(repository.search(filter, Set.of()).isEmpty());
    }
}
