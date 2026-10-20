package VietFreshHub.order;

import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.dto.OrderTrackingResponse;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderTrackingTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final ShopOrderRepository shops = mock(ShopOrderRepository.class);
    final OrderDeliveryReadRepository deliveries = mock(OrderDeliveryReadRepository.class);
    final OrderDeliveryHistoryReadRepository deliveryHistory = mock(OrderDeliveryHistoryReadRepository.class);
    final OrderStatusHistoryRepository orderHistory = mock(OrderStatusHistoryRepository.class);
    final DeliveryAssignmentService assignments = mock(DeliveryAssignmentService.class);
    final CustomerOrderServiceImpl service = new CustomerOrderServiceImpl(
            orders, shops, null, deliveries, deliveryHistory, orderHistory, assignments,
            null, null, null, null, null, null, null, null, null);
    final LocalDateTime utc = LocalDateTime.of(2026, 10, 20, 3, 0);
    ShopOrder child;
    OrderDeliveryView delivery;

    @BeforeEach void ownedOrder() {
        Order order = new Order(); order.setOrderId(10L); order.setOrderCode("VF-TEST");
        Shop shop = new Shop(); shop.setShopId(2L); shop.setShopName("Shop A");
        child = new ShopOrder(); child.setShopOrderId(20L); child.setShop(shop); child.setStatus("OUT_FOR_DELIVERY");
        delivery = new OrderDeliveryView(); delivery.setDeliveryId(30L); delivery.setShopOrder(child); delivery.setStatus("FAILED");
        when(orders.findByOrderIdAndCustomerUserId(10L, 1L)).thenReturn(Optional.of(order));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(10L)).thenReturn(List.of(child));
        when(deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(10L)).thenReturn(List.of(delivery));
    }
    OrderTrackingResponse.ShopTracking tracking() { return service.tracking(1L, 10L).shops().getFirst(); }
    OrderDeliveryHistoryView event(String status, LocalDateTime time) {
        var event = new OrderDeliveryHistoryView(); event.setDelivery(delivery); event.setStatus(status); event.setCreatedAt(time);
        return event;
    }
    void history(OrderDeliveryHistoryView... events) {
        when(deliveryHistory.findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(List.of(30L))).thenReturn(List.of(events));
    }

    @Test void exposesCurrentFailureReasonWithoutAttachingItToOldAttempts() {
        delivery.setFailureReason("  Không liên lạc được khách  ");
        history(event("FAILED", utc.minusDays(1)), event("FAILED", utc));
        var result = tracking();
        assertEquals("FAILED", result.deliveryStatusCode());
        assertEquals("Không liên lạc được khách", result.failureReason());
        assertTrue(result.events().stream().allMatch(e -> e.note() == null));
    }
    @ParameterizedTest @ValueSource(strings = {"", "   "})
    void blankReasonRemainsAbsent(String reason) { delivery.setFailureReason(reason); assertNull(tracking().failureReason()); }
    @Test void missingReasonRemainsAbsent() { assertNull(tracking().failureReason()); }
    @ParameterizedTest @ValueSource(strings = {"DELIVERED", "OUT_FOR_DELIVERY", "REDELIVERY_SCHEDULED"})
    void staleFailureReasonIsHiddenAfterStatusChanges(String status) {
        delivery.setStatus(status); delivery.setFailureReason("Lý do cũ");
        assertNull(tracking().failureReason()); assertEquals(status, tracking().deliveryStatusCode());
    }
    @Test void cancelledShopTakesPriorityOverStaleDelivery() {
        child.setStatus("CANCELLED"); delivery.setFailureReason("Lý do cũ");
        assertEquals("CANCELLED", tracking().deliveryStatusCode()); assertNull(tracking().failureReason());
    }
    @Test void redeliveryHasReadableLabelAndTimelineEntry() {
        delivery.setStatus("REDELIVERY_SCHEDULED"); history(event("REDELIVERY_SCHEDULED", utc));
        var result = tracking(); assertEquals("Đã hẹn giao lại", result.deliveryStatus());
        assertTrue(result.events().stream().anyMatch(e -> e.label().equals("Đã hẹn giao lại")));
    }
    @Test void timelineHandlesMissingTimeAndConvertsUtcToVietnamTime() {
        history(event("FAILED", null), event("OUT_FOR_DELIVERY", utc));
        var events = tracking().events();
        assertEquals(utc.plusHours(7), events.getFirst().occurredAt());
        assertNull(events.getLast().occurredAt());
    }
    @Test void confirmationTimestampIsSortedWithOtherEvents() {
        child.setConfirmedAt(utc.minusHours(1)); history(event("OUT_FOR_DELIVERY", utc));
        var events = tracking().events();
        assertEquals(utc.plusHours(6), events.getFirst().occurredAt());
        assertTrue(events.getFirst().label().contains("đã xác nhận"));
    }
    @Test void hidesUnrelatedStaffName() {
        var accepted = event("ACCEPTED", utc); accepted.setChangedBy(99L); history(accepted);
        when(assignments.getTrackingStaff(1L, 10L)).thenReturn(List.of(new DeliveryTrackingStaff(999L, 99L, "Không được lộ")));
        assertTrue(tracking().events().stream().noneMatch(e -> e.label().contains("Không được lộ")));
    }
    @Test void anotherCustomerCannotReadTrackingOrStaff() {
        assertThrows(ResourceNotFoundException.class, () -> service.tracking(999L, 10L));
        verifyNoInteractions(shops, deliveries, deliveryHistory, orderHistory, assignments);
    }
    @Test void orderWithoutDeliveryStillShowsShopStatus() {
        child.setStatus("PENDING");
        when(deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(10L)).thenReturn(List.of());
        assertEquals("PENDING", tracking().deliveryStatusCode());
        verifyNoInteractions(deliveryHistory, assignments);
    }
    @Test void deliveryCompletionDoesNotDuplicateShopCompletion() {
        child.setStatus("COMPLETED"); delivery.setStatus("DELIVERED");
        OrderStatusHistory completed = new OrderStatusHistory(); completed.setShopOrder(child);
        completed.setStatus("COMPLETED"); completed.setCreatedAt(utc);
        when(orderHistory.findByShopOrderOrderOrderIdOrderByCreatedAtAscHistoryIdAsc(10L)).thenReturn(List.of(completed));
        history(event("DELIVERED", utc));
        assertEquals(1, tracking().events().stream().filter(e -> "Giao hàng thành công".equals(e.label())).count());
        assertTrue(tracking().events().stream().noneMatch(e -> "ORDER".equals(e.kind()) && "Hoàn thành".equals(e.label())));
    }

    String renderTrackingPanel() {
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var context = new org.thymeleaf.context.Context(); context.setVariable("tracking", service.tracking(1L, 10L));
        // Render the real tracking panel, without unrelated shared navigation/security fragments.
        var template = new org.thymeleaf.TemplateSpec("customer/order-tracking", java.util.Set.of(".tracking-shops"),
                org.thymeleaf.templatemode.TemplateMode.HTML, null);
        return engine.process(template, context);
    }
    @Test void templateEscapesFailureReason() {
        delivery.setFailureReason("<script>alert('x')</script>");
        String html = renderTrackingPanel();
        assertTrue(html.contains("Lý do giao hàng thất bại:"));
        assertTrue(html.contains("&lt;script&gt;")); assertFalse(html.contains("<script>"));
    }
    @Test void templateExplainsMissingReason() {
        assertTrue(renderTrackingPanel().contains("Chưa ghi nhận lý do. Vui lòng liên hệ cửa hàng để được hỗ trợ."));
    }
    @Test void templateHidesFailurePanelForSuccessfulDelivery() {
        delivery.setStatus("DELIVERED"); delivery.setFailureReason("Lý do cũ");
        String html = renderTrackingPanel();
        assertFalse(html.contains("Lý do giao hàng thất bại:")); assertFalse(html.contains("Lý do cũ"));
    }
}
