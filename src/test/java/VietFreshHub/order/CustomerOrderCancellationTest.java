package VietFreshHub.order;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.*;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderCancellationTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final ShopOrderRepository shops = mock(ShopOrderRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final OrderDeliveryReadRepository deliveries = mock(OrderDeliveryReadRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final OrderInventoryReleaseService inventory = mock(OrderInventoryReleaseService.class);
    final EntityManager em = mock(EntityManager.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final CustomerOrderServiceImpl service = new CustomerOrderServiceImpl(
            orders, shops, payments, deliveries, null, null, null, users,
            null, null, null, null, null, inventory, em, events);
    Order order;
    ShopOrder child;
    Payment payment;

    @BeforeEach void pendingCodOrder() {
        User buyer = new User(); buyer.setUserId(1L); buyer.setStatus("ACTIVE");
        order = new Order(); order.setOrderId(10L); order.setCustomer(buyer);
        child = new ShopOrder(); child.setShopOrderId(20L); child.setOrder(order);
        CatalogVariant variant = new CatalogVariant(); variant.setVariantId(30L);
        OrderItem item = new OrderItem(); item.setOrderItemId(40L); item.setVariant(variant); item.setQuantity(2);
        child.setItems(List.of(item));
        PaymentMethod cod = new PaymentMethod(); cod.setMethodCode("COD"); cod.setName("COD");
        payment = new Payment(); payment.setPaymentMethod(cod);
        when(users.findByUserIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(orders.findByOrderIdAndCustomerUserId(10L, 1L)).thenReturn(Optional.of(order));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(10L)).thenReturn(List.of(child));
        when(payments.findByOrderOrderIdOrderByPaymentIdAsc(10L)).thenReturn(List.of(payment));
        when(deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(10L)).thenReturn(List.of());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "CONFIRMED"})
    void allowsCancellationBeforePreparation(String status) {
        order.setStatus(Order.Status.valueOf(status)); child.setStatus(status);
        assertTrue(service.actions(1L, 10L).cancellable());
        service.cancel(1L, 10L, "Đổi địa chỉ");
        assertEquals(Order.Status.CANCELLED, order.getStatus());
        assertEquals("CANCELLED", child.getStatus());
        assertEquals(Payment.Status.CANCELLED, payment.getStatus());
        verify(inventory).release(List.of(new OrderInventoryReleaseService.Line(40L, 30L, 2)), 1L);
        verify(events).publishEvent(new OrderCancelledEvent(10L, 1L));
        service.cancel(1L, 10L, null);
        verify(inventory, times(1)).release(anyList(), eq(1L));
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_DELIVERY", "OUT_FOR_DELIVERY", "COMPLETED", "CANCELLED"})
    void blocksAdvancedOrPartiallyCancelledOrders(String status) {
        child.setStatus(status); assertBlocked();
    }

    @Test void blocksPaidOrders() { order.setPaymentStatus("PAID"); assertBlocked(); }
    @Test void blocksOnlinePayments() { payment.getPaymentMethod().setMethodCode("VNPAY"); assertBlocked(); }
    @Test void blocksDiscountPendingReconciliation() { child.setDiscountTotal(BigDecimal.ONE); assertBlocked(); }
    @Test void blocksPickedUpDelivery() {
        OrderDeliveryView delivery = new OrderDeliveryView(); delivery.setStatus("PICKED_UP");
        when(deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(10L)).thenReturn(List.of(delivery));
        assertBlocked();
    }
    @Test void rejectsAnotherCustomersOrder() {
        when(orders.findByOrderIdAndCustomerUserId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(1L, 10L, null));
        verifyNoInteractions(inventory, events);
    }
    @Test void rejectsInactiveCustomer() {
        order.getCustomer().setStatus("BLOCKED");
        assertThrows(CustomerOrderException.class, () -> service.cancel(1L, 10L, null));
        verifyNoInteractions(inventory, events);
    }
    @Test void rejectsLongReason() {
        assertThrows(CustomerOrderException.class, () -> service.cancel(1L, 10L, "a".repeat(501)));
        verifyNoInteractions(inventory, events);
    }
    void assertBlocked() {
        assertFalse(service.actions(1L, 10L).cancellable());
        assertThrows(CustomerOrderException.class, () -> service.cancel(1L, 10L, null));
        assertEquals(Order.Status.PENDING, order.getStatus());
        verifyNoInteractions(inventory, events);
    }
}
