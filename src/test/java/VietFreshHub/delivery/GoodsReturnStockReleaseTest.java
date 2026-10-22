package VietFreshHub.delivery;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.entity.*;
import VietFreshHub.delivery.repository.*;
import VietFreshHub.delivery.service.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.service.*;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.exception.CustomerOrderException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoodsReturnStockReleaseTest {
    @Mock DeliveryAssignmentRepository assignments;
    @Mock DeliveryRepository deliveries;
    @Mock DeliveryService deliveryService;
    @Mock DeliveryStatusHistoryWriter writer;
    @Mock DeliveryStatusHistoryRepository history;
    @Mock AuthService auth;
    @Mock ShopService shops;
    @Mock OrderService orders;
    @Mock ApplicationEventPublisher events;
    @Mock DeliveryNotificationService notifications;
    @Mock OrderInventoryReleaseService inventory;
    @InjectMocks DeliveryAssignmentService service;
    AutoCloseable mocks;
    final Authentication authentication = mock(Authentication.class);
    Delivery delivery;
    DeliveryAssignment assignment;
    final List<OrderInventoryReleaseService.Line> lines = List.of(new OrderInventoryReleaseService.Line(10L, 11L, 2));
    @BeforeEach void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        delivery = new Delivery(); delivery.markFailed("Khách không nhận hàng");
        delivery.setPickedUpAt(LocalDateTime.now()); delivery.setFailureReason("Khách không nhận hàng");
        ShopOrder shopOrder = new ShopOrder();
        CatalogVariant variant = new CatalogVariant(); variant.setVariantId(11L);
        OrderItem item = new OrderItem(); item.setOrderItemId(10L); item.setVariant(variant); item.setQuantity(2);
        shopOrder.setItems(new ArrayList<>(List.of(item))); delivery.setShopOrder(shopOrder);
        assignment = new DeliveryAssignment(delivery, new User(), LocalDateTime.now()); assignment.accept(LocalDateTime.now());
        when(shops.getManagedShopId(authentication)).thenReturn(3L);
        when(auth.getCurrentUserId(authentication)).thenReturn(7L);
        when(deliveries.findForUpdateByDeliveryIdAndShopOrder_ShopId(1L, 3L)).thenReturn(Optional.of(delivery));
        when(assignments.findForUpdateByDelivery_DeliveryIdAndStatus(1L, DeliveryAssignmentStatus.ACCEPTED))
                .thenReturn(Optional.of(assignment));
    }
    @AfterEach void close() throws Exception { mocks.close(); }
    @Test void returnConfirmationReleasesStockBeforeCompletingAssignment() {
        service.confirmGoodsReturn(1L, authentication);
        verify(inventory).releaseReturnedGoods(lines, 7L);
        assertEquals(DeliveryAssignmentStatus.COMPLETED, assignment.getStatus());
        verify(writer).record(eq(delivery), eq(7L), anyString());
    }
    @Test void inconsistentStockPreventsReturnConfirmationAndNotification() {
        doThrow(new CustomerOrderException("Kho không khớp")).when(inventory).releaseReturnedGoods(lines, 7L);
        assertThrows(ResponseStatusException.class, () -> service.confirmGoodsReturn(1L, authentication));
        assertEquals(DeliveryAssignmentStatus.ACCEPTED, assignment.getStatus());
        verifyNoInteractions(writer, notifications, events);
    }
    @Test void duplicateConfirmationDoesNotReleaseAgain() {
        when(history.existsByDelivery_DeliveryIdAndStatusAndNoteAndChangedByIsNotNull(anyLong(), any(), anyString())).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.confirmGoodsReturn(1L, authentication));
        verifyNoInteractions(inventory);
    }
    @Test void deliveryStillOnRoadCannotReleaseStock() {
        delivery.startDelivery();
        assertThrows(ResponseStatusException.class, () -> service.confirmGoodsReturn(1L, authentication));
        verifyNoInteractions(inventory);
    }
}

