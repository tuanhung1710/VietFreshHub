package VietFreshHub.inventory.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.inventory.dto.JournalResponse;
import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.service.impl.InventoryServiceImpl;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryJournalTest {
    @Mock InventoryTransactionRepository transactions;
    @Mock ProductVariantRepository variants;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock ShopService shops;
    @Mock InventoryOrderLineRepository orderLines;
    @Mock ShopOrderRepository shopOrders;
    @Mock InventoryBatchRepository batches;
    @Mock StockAllocationRepository allocations;
    @InjectMocks InventoryServiceImpl service;

    @Test
    void journalBulkLoadsReferencesAndRetainsShopScopeSortingAndPaging() {
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopId(authentication)).thenReturn(77L);

        InventoryTransaction productWithoutVariant = event(1L, 101L, 301L, null, null);
        InventoryTransaction variantWithoutProduct = event(2L, 102L, 302L, "ORDER_ITEM", 900L);
        InventoryTransaction complete = event(3L, 103L, 303L, null, null);
        ProductVariant orphanVariant = new ProductVariant();
        orphanVariant.setVariantId(102L);
        orphanVariant.setProductId(202L);
        orphanVariant.setSku("SKU-102");
        ProductVariant foundVariant = new ProductVariant();
        foundVariant.setVariantId(103L);
        foundVariant.setProductId(203L);
        foundVariant.setSku("SKU-103");
        Product foundProduct = new Product();
        foundProduct.setProductId(203L);
        foundProduct.setShopId(77L);
        foundProduct.setName("Apple");
        User actor = new User();
        actor.setUserId(303L);
        actor.setFullName("Staff");

        when(transactions.search(eq(77L), isNull(), isNull(), isNull(), eq(""), isNull(), isNull(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productWithoutVariant, variantWithoutProduct, complete),
                        PageRequest.of(1, 5), 11));
        when(variants.findAllById(any())).thenReturn(List.of(orphanVariant, foundVariant));
        when(products.findAllById(any())).thenReturn(List.of(foundProduct));
        when(users.findAllById(any())).thenReturn(List.of(actor));
        when(orderLines.findAllById(any())).thenReturn(List.of());

        var page = service.journal(authentication, null, null, null, "", null, null, 2, 5);

        assertEquals(3, page.getContent().size());
        assertEquals("Sản phẩm không còn dữ liệu", page.getContent().get(0).getDisplayProductName());
        assertEquals("SKU không còn dữ liệu", page.getContent().get(0).getDisplaySku());
        assertEquals("Người dùng không còn dữ liệu", page.getContent().get(0).getDisplayActorName());
        assertEquals("Đơn hàng không còn dữ liệu", page.getContent().get(1).getReferenceLabel());
        assertEquals("Sản phẩm không còn dữ liệu", page.getContent().get(1).getDisplayProductName());
        assertEquals("SKU-102", page.getContent().get(1).getDisplaySku());
        assertEquals("Người dùng không còn dữ liệu", page.getContent().get(1).getDisplayActorName());
        assertEquals("Apple", page.getContent().get(2).getDisplayProductName());
        assertEquals("Staff", page.getContent().get(2).getDisplayActorName());

        verify(transactions).search(eq(77L), isNull(), isNull(), isNull(), eq(""), isNull(), isNull(),
                any(LocalDateTime.class), any(LocalDateTime.class), argThat(p -> p.getPageNumber() == 1
                        && p.getPageSize() == 5 && p.getSort().getOrderFor("createdAt").isDescending()
                        && p.getSort().getOrderFor("transactionId").isDescending()));
        verify(variants, times(1)).findAllById(any());
        verify(products, times(1)).findAllById(any());
        verify(users, times(1)).findAllById(any());
        verify(orderLines, times(1)).findAllById(any());
        verify(variants, never()).findById(anyLong());
        verify(products, never()).findById(anyLong());
        verify(users, never()).findById(anyLong());
    }

    @Test
    void journalDtoDisplaysNullTypeAndQuantityWithoutInventingValues() {
        InventoryTransaction event = event(4L, null, null, "ORDER_ITEM", null);
        event.setTransactionType(null);
        event.setQuantity(null);
        JournalResponse response = new JournalResponse(event, null, null, null, null);

        assertEquals("Loại biến động không xác định", response.getTransactionTypeLabel());
        assertEquals("Không có số liệu", response.getQuantityLabel());
        assertEquals("text-body-secondary", response.getQuantityCssClass());
        assertEquals("Không có loại biến động để diễn giải số lượng.", response.getQuantityDescription());
        assertEquals("Người dùng không còn dữ liệu", response.getDisplayActorName());
        assertEquals("Đơn hàng không còn dữ liệu", response.getReferenceLabel());
    }

    private InventoryTransaction event(Long id, Long variantId, Long actorId, String referenceType, Long referenceId) {
        InventoryTransaction event = new InventoryTransaction();
        event.setTransactionId(id);
        event.setVariantId(variantId);
        event.setTransactionType(StockTransactionType.RESTOCK);
        event.setQuantity(5);
        event.setReferenceType(referenceType);
        event.setReferenceId(referenceId);
        event.setCreatedBy(actorId);
        event.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return event;
    }
}
