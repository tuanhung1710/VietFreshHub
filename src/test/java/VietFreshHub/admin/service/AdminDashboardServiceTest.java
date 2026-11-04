package VietFreshHub.admin.service;

import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CatalogProductRepository products = mock(CatalogProductRepository.class);
    private final AdminDashboardService service = new AdminDashboardService(orders, orderItems, deliveries, products);

    @Test
    void classifiesEveryOrderStatusOnceAndKeepsTotalsConsistent() {
        givenDashboardData(List.of(
                count(Order.Status.PENDING, 2),
                count(Order.Status.CONFIRMED, 3),
                count(Order.Status.PROCESSING, 4),
                count(Order.Status.PARTIALLY_COMPLETED, 1),
                count(Order.Status.COMPLETED, 5),
                count(Order.Status.CANCELLED, 6)));

        var result = service.getDashboard("TODAY", null, null, 1);

        assertEquals(21, result.totalOrders());
        assertEquals(2, result.pendingConfirmationOrders());
        assertEquals(8, result.inDeliveryOrders());
        assertEquals(5, result.completedOrders());
        assertEquals(6, result.cancelledOrders());
        assertEquals(result.totalOrders(), result.pendingConfirmationOrders() + result.inDeliveryOrders()
                + result.completedOrders() + result.cancelledOrders());
    }

    @Test
    void pendingParentOrderWithProgressedShopOrderCountsAsInDelivery() {
        givenDashboardData(List.of(count(Order.Status.PENDING, 3)));
        when(orders.countPendingOrdersWithStartedShopOrder(any(), any())).thenReturn(1L);

        var result = service.getDashboard("TODAY", null, null, 1);

        assertEquals(3, result.totalOrders());
        assertEquals(2, result.pendingConfirmationOrders());
        assertEquals(1, result.inDeliveryOrders());
    }

    @Test
    void missingStatusesAreZeroWhenThereAreNoOrders() {
        givenDashboardData(List.of());

        var result = service.getDashboard("TODAY", null, null, 1);

        assertEquals(0, result.totalOrders());
        assertEquals(0, result.pendingConfirmationOrders());
        assertEquals(0, result.inDeliveryOrders());
        assertEquals(0, result.completedOrders());
        assertEquals(0, result.cancelledOrders());
    }

    @Test
    void mapsDeliveredProductAndShippingTotalsToDashboard() {
        givenDashboardData(List.of());
        when(deliveries.getDeliveredRevenue(any(), any())).thenReturn(new BigDecimal("880000"));
        when(deliveries.getDeliveredOrderTotals(any(), any())).thenReturn(new DeliveryRepository.DeliveredOrderTotals() {
            @Override public BigDecimal getProductValueAfterDiscount() { return new BigDecimal("850000"); }
            @Override public BigDecimal getShippingFees() { return new BigDecimal("30000"); }
        });

        var result = service.getDashboard("TODAY", null, null, 1);

        assertEquals(new BigDecimal("850000"), result.deliveredProductValueAfterDiscount());
        assertEquals(new BigDecimal("30000"), result.deliveredShippingFees());
        assertEquals(new BigDecimal("880000"), result.deliveredOrderValue());
    }

    @Test
    void dashboardDateRangeUsesVietnamMidnightConvertedToUtc() {
        givenDashboardData(List.of());
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 2);

        service.getDashboard("CUSTOM_RANGE", from, to, 1);

        ArgumentCaptor<LocalDateTime> boundaries = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orders).countOrdersByStatus(boundaries.capture(), boundaries.capture());
        assertEquals(LocalDateTime.of(2026, 2, 28, 17, 0), boundaries.getAllValues().get(0));
        assertEquals(LocalDateTime.of(2026, 3, 2, 17, 0), boundaries.getAllValues().get(1));
    }

    private void givenDashboardData(List<OrderRepository.StatusCount> counts) {
        when(orders.countOrdersByStatus(any(), any())).thenReturn(counts);
        when(orders.countPendingOrdersWithStartedShopOrder(any(), any())).thenReturn(0L);
        when(deliveries.getDeliveredRevenue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(deliveries.getDeliveredOrderTotals(any(), any())).thenReturn(new DeliveryRepository.DeliveredOrderTotals() {
            @Override public BigDecimal getProductValueAfterDiscount() { return BigDecimal.ZERO; }
            @Override public BigDecimal getShippingFees() { return BigDecimal.ZERO; }
        });
        when(deliveries.getDeliveredRevenueByHour(any(), any())).thenReturn(List.of());
        when(orderItems.findAdminBestSellers(any(), any(), any())).thenReturn(List.of());
        when(products.findProductsWithoutOrders(any())).thenReturn(new PageImpl<>(List.of()));
        when(products.countProductsByStatus()).thenReturn(List.of());
    }

    private OrderRepository.StatusCount count(Order.Status status, long count) {
        return new OrderRepository.StatusCount() {
            @Override public Order.Status getStatus() { return status; }
            @Override public Long getOrderCount() { return count; }
        };
    }
}
