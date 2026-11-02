package VietFreshHub.inventory.service;

import VietFreshHub.inventory.repository.InventoryAlertRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        jdbc.update("INSERT INTO dbo.products VALUES(1,7,'Xoài','ACTIVE',3)");
        jdbc.update("INSERT INTO dbo.product_variants VALUES(1,1,'Hộp','ACTIVE',2)");
        jdbc.update("INSERT INTO dbo.inventory_batches VALUES(1,1,?,'ACTIVE',5,3),(2,1,?,'ACTIVE',20,0)",
                InventoryRules.today(), InventoryRules.today().minusDays(1));
        repository = new InventoryAlertRepository(new NamedParameterJdbcTemplate(source));
    }

    @Test
    void queriesWorkWithoutAnAlertStateTableAndUseAvailableStockAndExpiryDates() {
        assertEquals(1, repository.lowStock(7L, InventoryRules.today()).size());
        assertEquals(1L, repository.expiry(7L, InventoryRules.today(), false).getFirst().entityId());
        assertEquals(2L, repository.expiry(7L, InventoryRules.today(), true).getFirst().entityId());
        jdbc.update("UPDATE dbo.inventory_batches SET expiry_date=? WHERE batch_id=1", InventoryRules.today().plusDays(4));
        assertTrue(repository.expiry(7L, InventoryRules.today(), false).isEmpty());
        assertFalse(repository.belongsToShop(8L, 1L, true));
    }

    @Test
    void fullAlertRefreshUsesRealQueriesWithoutAStateTableAndDoesNotRepeatNotifications() {
        var shops = mock(ShopRepository.class);
        var ownership = mock(ShopService.class);
        var notifications = mock(NotificationService.class);
        var service = new InventoryAlertService(repository, shops, ownership, notifications, new InventoryAlertStateStore());
        when(shops.findByShopIdForUpdate(7L)).thenReturn(Optional.of(new Shop()));
        when(ownership.findActiveManagerUserIds(7L)).thenReturn(List.of(9L));
        service.refresh(7L);
        service.refresh(7L);
        verify(notifications).createInAppForTransition(eq(9L), eq("LOW_STOCK"), anyString(), anyString(), eq("INVENTORY_VARIANT"), eq(1L));
        verify(notifications).createInAppForTransition(eq(9L), eq("NEAR_EXPIRY"), anyString(), anyString(), eq("INVENTORY_BATCH"), eq(1L));
        verify(notifications).createInAppForTransition(eq(9L), eq("EXPIRED_STOCK"), anyString(), anyString(), eq("INVENTORY_BATCH"), eq(2L));
        verifyNoMoreInteractions(notifications);
    }

}
