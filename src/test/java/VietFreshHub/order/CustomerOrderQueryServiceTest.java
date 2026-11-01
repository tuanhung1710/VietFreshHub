package VietFreshHub.order;

import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.impl.CustomerOrderQueryServiceImpl;
import VietFreshHub.payment.entity.Payment;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderQueryServiceTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderAddressRepository addresses = mock(OrderAddressRepository.class);
    final CustomerOrderReadRepository reads = mock(CustomerOrderReadRepository.class);
    final CustomerOrderQueryServiceImpl service = new CustomerOrderQueryServiceImpl(orders, addresses, reads);

    @Test void detailUsesSharedStageInsteadOfStaleParentAndKeepsPaymentIndependent() {
        var order = new Order();
        order.setStatus(Order.Status.COMPLETED);
        order.setPaymentStatus("UNPAID");
        when(orders.findByOrderIdAndCustomerUserId(10L,7L)).thenReturn(Optional.of(order));
        when(orders.findCustomerStage(10L,7L)).thenReturn(Optional.of("PREPARING"));
        when(addresses.findByOrderOrderId(10L)).thenReturn(Optional.of(new OrderAddress()));
        var cod = mock(CustomerOrderReadRepository.PaymentSummary.class);
        when(cod.getStatus()).thenReturn(Payment.Status.PENDING);
        when(cod.getMethodCode()).thenReturn("COD");
        when(reads.findPayments(7L,10L)).thenReturn(List.of(cod));
        var detail = service.getDetails(7L,10L);
        assertEquals("Đang chuẩn bị", detail.statusLabel());
        assertEquals("Chưa thu tiền", detail.paymentStatusLabel());
        assertEquals("Chờ thu COD", detail.payments().getFirst().statusLabel());
        verify(orders).findCustomerStage(10L,7L);
        verify(reads).findItems(7L,10L);
        verify(reads).findShops(7L,10L);
        verify(reads).findDeliveries(7L,10L);
        verify(reads).findPayments(7L,10L);
        verifyNoMoreInteractions(reads);
    }
    @Test void anotherCustomersOrderStopsBeforeReadingDetails() {
        when(orders.findByOrderIdAndCustomerUserId(10L,7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getDetails(7L,10L));
        verifyNoInteractions(addresses,reads);
        verify(orders,never()).findCustomerStage(anyLong(),anyLong());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"FAILED,Giao hàng thất bại","REDELIVERY_SCHEDULED,Chờ giao lại","DELIVERED,Đã giao hàng","OUT_FOR_DELIVERY,Đang giao"})
    void deliveryAndPaymentFieldsRemainShopScopedWithActualTimes(String status,String label) {
        when(orders.findByOrderIdAndCustomerUserId(10L,7L)).thenReturn(Optional.of(new Order()));
        when(orders.findCustomerStage(10L,7L)).thenReturn(Optional.of("SHIPPING"));
        when(addresses.findByOrderOrderId(10L)).thenReturn(Optional.of(new OrderAddress()));
        var shop=mock(CustomerOrderReadRepository.ShopSummary.class);
        when(shop.getShopOrderId()).thenReturn(20L); when(shop.getStatus()).thenReturn("OUT_FOR_DELIVERY");
        when(reads.findShops(7L,10L)).thenReturn(List.of(shop));
        var delivery=mock(CustomerOrderReadRepository.DeliverySummary.class);
        when(delivery.getShopOrderId()).thenReturn(20L); when(delivery.getStatus()).thenReturn(status);
        when(delivery.getPickedUpAt()).thenReturn(java.time.LocalDateTime.of(2026,10,20,3,0));
        when(delivery.getFailureReason()).thenReturn("  Không gặp khách  ");
        when(reads.findDeliveries(7L,10L)).thenReturn(List.of(delivery));
        var pay=mock(CustomerOrderReadRepository.PaymentSummary.class);
        when(pay.getStatus()).thenReturn(Payment.Status.PAID); when(pay.getMethodCode()).thenReturn("VNPAY");
        when(pay.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026,10,20,2,0));
        when(pay.getTransactionReference()).thenReturn("VN-123");
        when(reads.findPayments(7L,10L)).thenReturn(List.of(pay));
        var detail=service.getDetails(7L,10L);
        assertEquals("Vận chuyển",detail.statusLabel());
        assertEquals("OUT_FOR_DELIVERY",detail.shops().getFirst().statusCode());
        assertEquals(label,detail.shops().getFirst().delivery().statusLabel());
        assertEquals(java.time.LocalDateTime.of(2026,10,20,10,0),detail.shops().getFirst().delivery().pickedUpAt());
        assertEquals("FAILED".equals(status) ? "Không gặp khách" : null,detail.shops().getFirst().delivery().failureReason());
        assertNull(detail.shops().getFirst().delivery().deliveredAt());
        assertEquals("Đã thanh toán",detail.payments().getFirst().statusLabel());
        assertEquals("VN-123",detail.payments().getFirst().transactionReference());
        assertEquals(java.time.LocalDateTime.of(2026,10,20,9,0),detail.payments().getFirst().createdAt());
    }
}
