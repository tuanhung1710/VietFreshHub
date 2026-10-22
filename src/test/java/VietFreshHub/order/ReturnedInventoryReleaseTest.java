package VietFreshHub.order;

import VietFreshHub.auth.entity.User;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.checkout.entity.*;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.exception.CustomerOrderException;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnedInventoryReleaseTest {
    final EntityManager em = mock(EntityManager.class);
    final OrderInventoryReleaseService service = new OrderInventoryReleaseService(em);
    final CheckoutInventoryBatch batch = new CheckoutInventoryBatch();
    final CheckoutStockAllocation allocation = new CheckoutStockAllocation();
    final List<CheckoutStockMovement> journal = new ArrayList<>();
    final List<OrderInventoryReleaseService.Line> lines = List.of(new OrderInventoryReleaseService.Line(1L, 2L, 3));
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        CatalogVariant variant = new CatalogVariant(); variant.setVariantId(2L);
        batch.setBatchId(4L); batch.setVariant(variant); batch.setQuantityOnHand(10); batch.setReservedQuantity(5);
        OrderItem item = new OrderItem(); item.setOrderItemId(1L);
        allocation.setOrderItem(item); allocation.setBatch(batch); allocation.setQuantity(3);
        CheckoutStockMovement reservation = new CheckoutStockMovement(); reservation.setBatch(batch); reservation.setVariant(variant);
        reservation.setReferenceId(1L); reservation.setTransactionType("RESERVE"); reservation.setQuantity(3); journal.add(reservation);
        TypedQuery<CheckoutStockAllocation> allocations = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CheckoutStockAllocation.class))).thenReturn(allocations);
        when(allocations.setParameter(anyString(), any())).thenReturn(allocations);
        when(allocations.setLockMode(any())).thenReturn(allocations);
        when(allocations.getResultList()).thenReturn(List.of(allocation));
        TypedQuery<CheckoutStockMovement> movements = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CheckoutStockMovement.class))).thenReturn(movements);
        when(movements.setParameter(anyString(), any())).thenReturn(movements);
        when(movements.getResultList()).thenReturn(journal);
        when(em.find(CheckoutInventoryBatch.class, 4L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(batch);
        when(em.getReference(User.class, 7L)).thenReturn(new User());
        doAnswer(call -> { journal.add(call.getArgument(0)); return null; }).when(em).persist(any(CheckoutStockMovement.class));
    }
    @Test void returnReleasesOnlyItsOwnReservationAndRetryDoesNotAddStock() {
        service.releaseReturnedGoods(lines, 7L);
        assertEquals(2, batch.getReservedQuantity());
        assertEquals(10, batch.getQuantityOnHand());
        assertEquals(CheckoutStockAllocation.Status.RELEASED, allocation.getStatus());
        assertEquals(-3, journal.getLast().getQuantity());
        service.releaseReturnedGoods(lines, 7L);
        assertEquals(2, batch.getReservedQuantity());
        assertEquals(2, journal.size());
    }
    @Test void missingAuditRejectsReleaseWithoutChangingStock() {
        journal.clear();
        assertThrows(CustomerOrderException.class, () -> service.releaseReturnedGoods(lines, 7L));
        assertEquals(5, batch.getReservedQuantity());
        assertEquals(CheckoutStockAllocation.Status.RESERVED, allocation.getStatus());
    }
}
