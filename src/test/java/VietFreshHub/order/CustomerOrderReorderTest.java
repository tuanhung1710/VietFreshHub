package VietFreshHub.order;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.*;
import VietFreshHub.cart.repository.*;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.exception.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.*;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderReorderTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final ShopOrderRepository shops = mock(ShopOrderRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final CartRepository carts = mock(CartRepository.class);
    final CartItemRepository items = mock(CartItemRepository.class);
    final CatalogVariantRepository variants = mock(CatalogVariantRepository.class);
    final CatalogService catalog = mock(CatalogService.class);
    final CheckoutInventoryBatchRepository stock = mock(CheckoutInventoryBatchRepository.class);
    final OrderInventoryReleaseService inventory = mock(OrderInventoryReleaseService.class);
    final CustomerOrderServiceImpl service = new CustomerOrderServiceImpl(orders, shops, null, null, null,
            null, null, users, carts, items, variants, catalog, stock, inventory, mock(EntityManager.class), null);
    User buyer;
    ShopOrder child;
    CatalogVariant variant;
    OrderItem original;

    @BeforeEach void ownedOrder() {
        buyer = new User(); buyer.setUserId(1L); buyer.setStatus("ACTIVE");
        when(users.findByUserIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(orders.findByOrderIdAndCustomerUserId(10L, 1L)).thenReturn(Optional.of(new Order()));
        variant = new CatalogVariant(); variant.setVariantId(30L); variant.setPrice(new BigDecimal("25000"));
        original = line(variant, 2); original.setUnitPrice(new BigDecimal("10000"));
        child = new ShopOrder(); child.setItems(List.of(original));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(10L)).thenReturn(List.of(child));
        when(variants.findById(30L)).thenReturn(Optional.of(variant));
        when(catalog.isPurchasable(variant)).thenReturn(true);
        when(stock.getAvailableStockByVariantId(30L)).thenReturn(10);
    }
    OrderItem line(CatalogVariant variant, int quantity) {
        OrderItem line = new OrderItem(); line.setVariant(variant); line.setQuantity(quantity);
        line.setProductNameSnapshot("Tên sản phẩm cũ"); line.setVariantNameSnapshot("Loại cũ"); return line;
    }
    CartItem savedItem() {
        var saved = ArgumentCaptor.forClass(CartItem.class); verify(items).save(saved.capture()); return saved.getValue();
    }
    @Test void createsCartAtCurrentPriceWithoutCreatingAnOrderOrReservingStock() {
        var result = service.reorder(1L, 10L);
        assertEquals(1, result.addedLines()); assertEquals(2, result.addedUnits()); assertTrue(result.skipped().isEmpty());
        CartItem saved = savedItem(); assertEquals(2, saved.getQuantity()); assertEquals(variant.getPrice(), saved.getUnitPriceSnapshot());
        assertSame(buyer, saved.getCart().getUser()); verify(carts).save(saved.getCart());
        verify(orders, never()).save(any()); verifyNoInteractions(inventory);
        assertEquals(new BigDecimal("10000"), original.getUnitPrice());
    }
    @Test void addsToExistingQuantityAndRefreshesPrice() {
        Cart cart = new Cart(); cart.setCartId(40L);
        CartItem existing = new CartItem(); existing.setCart(cart); existing.setVariant(variant); existing.setQuantity(3);
        when(carts.findByUserUserIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(cart));
        when(items.findByCartCartIdAndVariantVariantId(40L, 30L)).thenReturn(Optional.of(existing));
        assertEquals(2, service.reorder(1L, 10L).addedUnits());
        assertEquals(5, existing.getQuantity()); assertEquals(variant.getPrice(), existing.getUnitPriceSnapshot());
        verify(carts, never()).save(any());
    }
    @Test void checksCombinedQuantityWithoutSilentlyReducingIt() {
        Cart cart = new Cart(); cart.setCartId(40L);
        CartItem existing = new CartItem(); existing.setQuantity(9);
        when(carts.findByUserUserIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(cart));
        when(items.findByCartCartIdAndVariantVariantId(40L, 30L)).thenReturn(Optional.of(existing));
        var result = service.reorder(1L, 10L);
        assertEquals(0, result.addedUnits()); assertEquals(1, result.skipped().size()); assertEquals(9, existing.getQuantity());
        verify(items, never()).save(any());
    }
    @Test void unavailableVariantIsReportedWithoutCreatingAnEmptyCart() {
        when(catalog.isPurchasable(variant)).thenReturn(false);
        var result = service.reorder(1L, 10L);
        assertEquals("Tên sản phẩm cũ", result.skipped().getFirst().productName());
        assertEquals(0, result.addedLines()); verify(carts, never()).save(any()); verify(items, never()).save(any());
    }
    @Test void missingVariantIsReported() {
        when(variants.findById(30L)).thenReturn(Optional.empty());
        assertEquals(1, service.reorder(1L, 10L).skipped().size()); verifyNoInteractions(catalog);
    }
    @Test void combinesRepeatedVariantBeforeCheckingStock() {
        child.setItems(List.of(original, line(variant, 3)));
        assertEquals(5, service.reorder(1L, 10L).addedUnits()); assertEquals(5, savedItem().getQuantity());
        verify(stock, times(1)).getAvailableStockByVariantId(30L);
    }
    @Test void rejectsQuantityOverflow() {
        child.setItems(List.of(line(variant, Integer.MAX_VALUE), line(variant, 1)));
        assertEquals(1, service.reorder(1L, 10L).skipped().size()); verify(items, never()).save(any());
    }
    @Test void rejectsAnotherCustomersOrderBeforeReadingItems() {
        when(orders.findByOrderIdAndCustomerUserId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.reorder(1L, 10L));
        verifyNoInteractions(shops, carts, items, variants, inventory);
    }
    @Test void rejectsInactiveCustomer() {
        buyer.setStatus("BLOCKED"); assertThrows(CustomerOrderException.class, () -> service.reorder(1L, 10L));
        verifyNoInteractions(orders, shops, carts, items);
    }
    @Test void emptyOrderDoesNotCreateCart() {
        child.setItems(List.of()); assertEquals(0, service.reorder(1L, 10L).addedLines());
        verify(carts, never()).save(any()); verifyNoInteractions(items, inventory);
    }
}
