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
        jdbc.execute("CREATE TABLE dbo.shops(shop_id BIGINT,shop_name VARCHAR,status VARCHAR,availability_status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.shop_locations(shop_id BIGINT,province VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.categories(category_id BIGINT,name VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.product_categories(product_id BIGINT,category_id BIGINT)");
        jdbc.execute("CREATE TABLE dbo.product_variants(variant_id BIGINT,product_id BIGINT,status VARCHAR,price DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE dbo.inventory_batches(batch_id BIGINT,variant_id BIGINT,status VARCHAR,expiry_date DATE,quantity_on_hand INT,reserved_quantity INT)");
        jdbc.execute("CREATE TABLE dbo.batch_markdowns(batch_id BIGINT,status VARCHAR,start_date DATE,end_date DATE,price DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE dbo.order_items(variant_id BIGINT, shop_order_id BIGINT, quantity INT)");
        jdbc.execute("CREATE TABLE dbo.shop_orders(shop_order_id BIGINT, status VARCHAR)");
        jdbc.execute("CREATE TABLE dbo.reviews(product_id BIGINT, rating INT, status VARCHAR)");
        jdbc.update("INSERT INTO dbo.shops VALUES(1,'Vườn Sơn','ACTIVE','OPEN'),(2,'Green Orchard','ACTIVE','OPEN')");
        jdbc.update("INSERT INTO dbo.shop_locations VALUES(1,'Hồ Chí Minh'),(2,'Hà Nội')");
        jdbc.update("INSERT INTO dbo.categories VALUES(7,'Trái cây theo mùa')");
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
    void closedShopsAndSoldOutProductsNeverAppear() {
        jdbc.update("UPDATE dbo.shops SET availability_status='CLOSED' WHERE shop_id=2");
        assertEquals(List.of(1L), repository.search(filter, Set.of(7L)).getContent());
        jdbc.update("UPDATE dbo.inventory_batches SET quantity_on_hand=0");
        assertTrue(repository.search(filter, Set.of(7L)).isEmpty());
    }

    @Test
    void searchEscapesSqlWildcardsAndRejectsInactiveCategory() {
        filter.setKeyword("%");
        assertTrue(repository.search(filter, Set.of(7L)).isEmpty());
        filter.setKeyword("");
        assertTrue(repository.search(filter, Set.of()).isEmpty());
    }

    @Test
    void priceSortingUsesTheDisplayedPriceAndStableProductIdTieBreak() {
        filter.setSort("price_asc");
        assertEquals(List.of(1L, 2L), repository.search(filter, Set.of(7L)).getContent());
        filter.setSort("price_desc");
        assertEquals(List.of(2L, 1L), repository.search(filter, Set.of(7L)).getContent());
    }

    @Test
    void salesOnlyCountCompletedOrders() {
        jdbc.update("INSERT INTO dbo.shop_orders VALUES(1,'COMPLETED'),(2,'CANCELLED')");
        jdbc.update("INSERT INTO dbo.order_items VALUES(1,1,5),(2,2,999)");
        filter.setSort("best_selling");
        assertEquals(List.of(1L, 2L), repository.search(filter, Set.of(7L)).getContent());
    }

    @Test
    void ratingIgnoresUnapprovedReviewsAndUsesFractionalAverage() {
        jdbc.update("INSERT INTO dbo.reviews VALUES(1,4,'APPROVED'),(1,5,'APPROVED'),(2,4,'APPROVED'),(2,5,'PENDING')");
        filter.setSort("rating");
        assertEquals(List.of(1L, 2L), repository.search(filter, Set.of(7L)).getContent());
    }

    @Test
    void shopFilterCombinesWithOtherFilters() {
        filter.setShopId(2L);
        assertEquals(List.of(2L), repository.search(filter, Set.of(7L)).getContent());
        filter.setMaxPrice(new BigDecimal("16000"));
        assertTrue(repository.search(filter, Set.of(7L)).isEmpty());
    }

    @Test
    void multipleShopsPlacesAndMinimumRatingCombineWithoutDuplicatingProducts() {
        jdbc.update("INSERT INTO dbo.reviews VALUES(1,5,'APPROVED'),(1,4,'APPROVED'),(2,3,'APPROVED'),(2,5,'PENDING')");
        filter.setShopIds(List.of(1L, 2L));
        filter.setPlaces(List.of("Hồ Chí Minh", "Hà Nội"));
        filter.setMinRating(4);
        assertEquals(List.of(1L), repository.search(filter, Set.of(7L)).getContent());
        filter.setPlaces(List.of("Hà Nội"));
        assertTrue(repository.search(filter, Set.of(7L)).isEmpty());
    }

    @Test
    void searchIncludesSellerAndActiveCategoryNames() {
        filter.setKeyword("green");
        assertEquals(List.of(2L), repository.search(filter, Set.of(7L)).getContent());
        filter.setKeyword("theo mùa");
        assertEquals(List.of(1L, 2L), repository.search(filter, Set.of(7L)).getContent());
    }

    @Test
    void pageSizesAndOutOfRangePagesMatchTemplatePagination() {
        for (long id = 100; id < 125; id++) {
            jdbc.update("INSERT INTO dbo.products VALUES(?,1,'Xoài','ACTIVE',0)", id);
            jdbc.update("INSERT INTO dbo.product_categories VALUES(?,7)", id);
            jdbc.update("INSERT INTO dbo.product_variants VALUES(?,?,'ACTIVE',50000)", id, id);
            jdbc.update("INSERT INTO dbo.inventory_batches VALUES(?,?,'ACTIVE',?,10,0)", id, id, InventoryRules.today());
        }
        for (int size : List.of(5, 10, 20, 50)) {
            filter.setPageSize(size);
            filter.setPage(1);
            assertEquals(Math.min(27, size), repository.search(filter, Set.of(7L)).getNumberOfElements());
            filter.setPage(999);
            var last = repository.search(filter, Set.of(7L));
            assertEquals(last.getTotalPages() - 1, last.getNumber());
            assertFalse(last.isEmpty());
        }
        filter.setKeyword("không có");
        var empty = repository.search(filter, Set.of(7L));
        assertEquals(0, empty.getNumber());
        assertTrue(empty.isEmpty());
    }

    @Test
    void cardMetricsUseApprovedReviewsAndCompletedSales() {
        jdbc.update("INSERT INTO dbo.reviews VALUES(1,4,'APPROVED'),(1,5,'APPROVED'),(1,1,'REJECTED')");
        jdbc.update("INSERT INTO dbo.shop_orders VALUES(1,'COMPLETED'),(2,'CANCELLED')");
        jdbc.update("INSERT INTO dbo.order_items VALUES(1,1,8),(1,2,999)");
        var metrics = repository.metrics(List.of(1L)).get(1L);
        assertEquals(0, new BigDecimal("4.5").compareTo(metrics.rating()));
        assertEquals(8, metrics.sales());
        assertEquals("Hồ Chí Minh", metrics.place());
    }
}
