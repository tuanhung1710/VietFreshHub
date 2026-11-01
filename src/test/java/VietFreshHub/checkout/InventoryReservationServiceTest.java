package VietFreshHub.checkout;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import VietFreshHub.checkout.exception.CheckoutStockException;
import VietFreshHub.checkout.service.InventoryReservationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit evidence for lock/refresh and business error only. SQL race/rollback requires the SQL Server suite. */
class InventoryReservationServiceTest {
    @Test void refreshesAlreadyManagedBatchAfterLockBeforeCheckingAvailability() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<CheckoutInventoryBatch> query = mock(TypedQuery.class);
        CheckoutInventoryBatch batch = new CheckoutInventoryBatch();
        batch.setQuantityOnHand(5); batch.setReservedQuantity(0);
        when(entityManager.createQuery(anyString(), eq(CheckoutInventoryBatch.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(batch));
        doAnswer(call -> { batch.setReservedQuantity(4); return null; })
                .when(entityManager).refresh(batch, LockModeType.PESSIMISTIC_WRITE);
        CatalogProduct product = new CatalogProduct(); product.setName("Xoài");
        CatalogVariant variant = new CatalogVariant(); variant.setVariantId(11L);
        variant.setVariantName("Hộp 1 kg"); variant.setProduct(product);
        when(entityManager.getReference(CatalogVariant.class, 11L)).thenReturn(variant);

        InventoryReservationService service = new InventoryReservationService(entityManager);
        CheckoutStockException error = assertThrows(CheckoutStockException.class,
                () -> service.reserve(List.of(new InventoryReservationService.Reservation(80L, 11L, 3)), 7L));
        assertEquals(11L, error.getVariantId());
        assertEquals(3, error.getRequestedQuantity());
        assertEquals(1, error.getAvailableStock());
        assertTrue(error.getMessage().contains("Xoài — Hộp 1 kg"));
        assertEquals(4, batch.getReservedQuantity());
        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(batch, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager, never()).persist(any());
    }
}
