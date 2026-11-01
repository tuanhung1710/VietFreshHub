package VietFreshHub.product;

import VietFreshHub.product.repository.ProductManagementRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductManagementRepositoryInventoryTest {
    @Test
    void countsInventoryBatchesAndKeepsQueryScopedToShop() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{0L, 0L, 0L, 4L});
        ProductManagementRepositoryImpl repository = repository(entityManager);

        var result = repository.inventoryAttentionSummary(42L, LocalDate.of(2026, 11, 1));

        assertEquals(0, result.lowVariants());
        assertEquals(0, result.nearBatches());
        assertEquals(0, result.expiredBatches());
        assertEquals(4, result.inventoryBatches());
        assertEquals(true, result.hasInventoryData());
        assertEquals(false, result.hasAlerts());
        verify(query).setParameter("shop", 42L);
        verify(query).setParameter("today", LocalDate.of(2026, 11, 1));
    }

    @Test
    void queryFailurePropagatesInsteadOfBecomingZeroAlerts() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new PersistenceException("inventory query failed"));

        assertThrows(PersistenceException.class,
                () -> repository(entityManager).inventoryAttentionSummary(42L, LocalDate.now()));
    }

    @Test
    void emptyInventoryIsDifferentFromInventoryWithoutAlerts() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{0L, 0L, 0L, 0L});

        var result = repository(entityManager).inventoryAttentionSummary(42L, LocalDate.now());

        assertEquals(false, result.hasInventoryData());
        assertEquals(false, result.hasAlerts());
    }

    private ProductManagementRepositoryImpl repository(EntityManager entityManager) {
        ProductManagementRepositoryImpl repository = new ProductManagementRepositoryImpl();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }
}
