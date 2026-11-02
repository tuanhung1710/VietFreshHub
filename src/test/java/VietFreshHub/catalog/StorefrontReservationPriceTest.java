package VietFreshHub.catalog;

import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import VietFreshHub.checkout.entity.CheckoutStockAllocation;
import VietFreshHub.checkout.service.InventoryReservationService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.BatchMarkdownLookup;
import VietFreshHub.order.entity.OrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorefrontReservationPriceTest {
    @Test
    @SuppressWarnings("unchecked")
    void checkoutOnlyAllocatesLotsAtTheQuotedPrice() {
        var em = mock(EntityManager.class);
        var markdowns = mock(BatchMarkdownLookup.class);
        var service = new InventoryReservationService(em,markdowns);
        TypedQuery<CheckoutInventoryBatch> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(CheckoutInventoryBatch.class))).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        var variant = new CatalogVariant(); variant.setVariantId(7L); variant.setPrice(new BigDecimal("50000"));
        var discounted = batch(1L,variant,2);
        var fullPrice = batch(2L,variant,20);
        when(query.getResultList()).thenReturn(List.of(fullPrice,discounted));
        var item = new OrderItem(); item.setUnitPrice(new BigDecimal("30000"));
        when(em.find(OrderItem.class,9L)).thenReturn(item);
        when(em.getReference(OrderItem.class,9L)).thenReturn(item);
        var markdown = new BatchMarkdown(); markdown.setBatchId(1L); markdown.setPrice(new BigDecimal("30000"));
        markdown.setStartDate(InventoryRules.today()); markdown.setEndDate(InventoryRules.today());
        when(markdowns.findActive(any())).thenReturn(List.of(markdown));
        assertThrows(CheckoutException.class,() -> service.reserve(List.of(new InventoryReservationService.Reservation(9L,7L,3)),1L));
        assertEquals(0,fullPrice.getReservedQuantity());
        service.reserve(List.of(new InventoryReservationService.Reservation(9L,7L,2)),1L);
        assertEquals(2,discounted.getReservedQuantity());
        assertEquals(0,fullPrice.getReservedQuantity());
        var persisted = ArgumentCaptor.forClass(Object.class);
        verify(em,times(2)).persist(persisted.capture());
        var allocation = (CheckoutStockAllocation) persisted.getAllValues().stream().filter(CheckoutStockAllocation.class::isInstance).findFirst().orElseThrow();
        assertEquals(1L,allocation.getBatch().getBatchId());
    }

    private CheckoutInventoryBatch batch(Long id, CatalogVariant variant, int quantity) {
        var batch = new CheckoutInventoryBatch(); batch.setBatchId(id); batch.setVariant(variant);
        batch.setQuantityOnHand(quantity); batch.setExpiryDate(InventoryRules.today());
        return batch;
    }
}
