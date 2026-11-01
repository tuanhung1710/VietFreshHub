package VietFreshHub.order;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.delivery.repository.*;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.*;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.service.PaymentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopCustomerOrderCancellationTest {
    @Mock ShopOrderRepository shops;
    @Mock PaymentRepository payments;
    @Mock PaymentService paymentService;
    @Mock OrderDeliveryReadRepository deliveries;
    @Mock UserRepository users;
    @Mock OrderInventoryReleaseService inventory;
    @Mock OrderService lifecycle;
    @Mock OrderNotificationService notifications;
    @Mock EntityManager em;
    @InjectMocks ShopCustomerOrderServiceImpl service;
    AutoCloseable mocks;
    ShopOrder shop;
    Order order;
    Payment payment;

    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        var buyer=new User(); buyer.setUserId(1L); buyer.setStatus("ACTIVE");
        order=new Order(); order.setOrderId(10L); order.setCustomer(buyer); order.setGrandTotal(new BigDecimal("100000"));
        shop=new ShopOrder(); shop.setShopOrderId(20L); shop.setOrder(order); shop.setStatus("PENDING");
        var variant=new CatalogVariant(); variant.setVariantId(30L);
        var item=new OrderItem(); item.setOrderItemId(40L); item.setVariant(variant); item.setQuantity(2);
        shop.setItems(new ArrayList<>(List.of(item)));
        var method=new PaymentMethod(); method.setMethodCode("COD");
        payment=new Payment(); payment.setOrder(order); payment.setPaymentMethod(method);
        when(users.findByUserIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(shops.findByShopOrderIdAndOrderCustomerUserId(20L,1L)).thenReturn(Optional.of(shop));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(10L)).thenReturn(List.of(shop));
        when(payments.findByOrderOrderIdOrderByPaymentIdAsc(10L)).thenReturn(List.of(payment));
        when(deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(20L))).thenReturn(List.of());
    }
    @AfterEach void close() throws Exception { mocks.close(); }
    @Test void pendingCodShopCanBeCancelledAndStockReleased() {
        assertTrue(service.shopActions(1L,20L).cancellable());
        service.cancelShop(1L,20L,"Đổi địa chỉ");
        assertEquals("CANCELLED",shop.getStatus()); assertEquals(Order.Status.CANCELLED,order.getStatus());
        assertEquals(Payment.Status.CANCELLED,payment.getStatus());
        verify(inventory).release(List.of(new OrderInventoryReleaseService.Line(40L,30L,2)),1L);
        verify(lifecycle).reconcileCodPayment(10L); verify(notifications).notifyCancelledByCustomer(shop);
    }
    @Test void confirmedShopCannotUseTheOldAggregateCancellationPolicy() {
        shop.setStatus("CONFIRMED"); shop.setConfirmedAt(LocalDateTime.now());
        assertFalse(service.shopActions(1L,20L).cancellable());
        assertThrows(CustomerOrderException.class,() -> service.cancelShop(1L,20L,null));
        assertEquals("CONFIRMED",shop.getStatus()); verifyNoInteractions(inventory,notifications);
    }
    @Test void unreconciledOnlinePaymentPreventsCancellation() {
        payment.getPaymentMethod().setMethodCode("BANK_TRANSFER");
        assertFalse(service.shopActions(1L,20L).cancellable());
        assertThrows(CustomerOrderException.class,() -> service.cancelShop(1L,20L,null));
        verifyNoInteractions(inventory,notifications);
    }
}
