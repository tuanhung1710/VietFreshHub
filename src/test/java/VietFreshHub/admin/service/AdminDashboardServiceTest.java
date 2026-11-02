package VietFreshHub.admin.service;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminDashboardServiceTest {
    private static final ZoneId HCMC = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CatalogProductRepository products = mock(CatalogProductRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InventoryBatchRepository inventory = mock(InventoryBatchRepository.class);
    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(orders, orderItems, deliveries, products, users, inventory);
        when(orders.countOrdersByStatus(any(), any())).thenReturn(List.of());
        when(orders.findOrderVolumeByHour(any(), any())).thenReturn(List.of());
        when(deliveries.countSuccessfullyDeliveredOrders(any(), any())).thenReturn(0L);
        when(deliveries.countCancelledOrFailedOrders(any(), any())).thenReturn(0L);
        when(orderItems.findAdminBestSellers(any(), any(), any())).thenReturn(List.of());
        DeliveryRepository.RevenueTotal totals = mock(DeliveryRepository.RevenueTotal.class);
        when(totals.getRevenue()).thenReturn(BigDecimal.ZERO);
        when(totals.getDeliveredShopOrders()).thenReturn(0L);
        when(deliveries.getDeliveredRevenue(any(), any())).thenReturn(totals);
        when(deliveries.getDeliveredRevenueByHour(any(), any())).thenReturn(List.of());
        when(products.countProductsByStatus()).thenReturn(List.of());
        when(products.findProductsWithoutOrders(any())).thenReturn(Page.empty());
        when(users.countCustomers()).thenReturn(0L);
        when(users.countCustomersWithSuccessfulPurchase()).thenReturn(0L);
        when(users.countReturningCustomers()).thenReturn(0L);
        when(inventory.summarizeByProductVariant(any())).thenReturn(List.of());
        when(inventory.summarizeByShopAndUnit(any())).thenReturn(List.of());
        when(inventory.findApproachingExpiry(any(), any())).thenReturn(Page.empty());
    }

    @Test
    void todayUsesHcmcMidnightAndExclusiveNextDayBoundary() {
        LocalDate today = LocalDate.now(HCMC);
        assertWindow("TODAY", today, today);
    }

    @Test
    void lastSevenDaysIncludesTodayAndPreviousSixDays() {
        LocalDate today = LocalDate.now(HCMC);
        assertWindow("LAST_7_DAYS", today.minusDays(6), today);
    }

    @Test
    void lastThirtyDaysIncludesTodayAndPreviousTwentyNineDays() {
        LocalDate today = LocalDate.now(HCMC);
        assertWindow("LAST_30_DAYS", today.minusDays(29), today);
    }

    @Test
    void thisMonthStartsAtFirstLocalDayAndEndsAtExclusiveNextDay() {
        LocalDate today = LocalDate.now(HCMC);
        assertWindow("THIS_MONTH", today.withDayOfMonth(1), today);
    }

    @Test
    void customRangeIsInclusiveByDateAndInventoryRemainsCurrent() {
        LocalDate from = LocalDate.of(2026, 10, 3);
        LocalDate to = LocalDate.of(2026, 10, 9);
        service.getDashboard("CUSTOM_RANGE", from, to, 1, 1);
        LocalDateTime expectedFrom = from.atStartOfDay(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime expectedTo = to.plusDays(1).atStartOfDay(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        verify(orders).countOrdersByStatus(expectedFrom, expectedTo);
        verify(deliveries).getDeliveredRevenue(expectedFrom, expectedTo);
        verify(users).countNewCustomersByHour(expectedFrom, expectedTo);
        verify(deliveries).countSuccessfullyDeliveredOrders(expectedFrom, expectedTo);
        verify(inventory).summarizeByProductVariant(LocalDate.now(HCMC));
        verify(inventory).summarizeByShopAndUnit(LocalDate.now(HCMC));
        verify(inventory).findApproachingExpiry(eq(LocalDate.now(HCMC)), any());
    }

    @Test
    void customRangeRejectsMissingOrReversedDatesBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.getDashboard("CUSTOM_RANGE", null, null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.getDashboard("CUSTOM_RANGE",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9), 1, 1));
        verifyNoInteractions(orders, deliveries, orderItems, products, users);
    }

    @Test
    void revenueCardAndChartUseTheSameDeliveredShopOrderAmounts() {
        DeliveryRepository.RevenueTotal totals = mock(DeliveryRepository.RevenueTotal.class);
        when(totals.getRevenue()).thenReturn(new BigDecimal("150.00"));
        when(totals.getDeliveredShopOrders()).thenReturn(2L);
        when(deliveries.getDeliveredRevenue(any(), any())).thenReturn(totals);
        DeliveryRepository.RevenueByHour first = hour(2026, 10, 2, 18, "100.00");
        DeliveryRepository.RevenueByHour second = hour(2026, 10, 3, 18, "50.00");
        when(deliveries.getDeliveredRevenueByHour(any(), any())).thenReturn(List.of(first, second));

        var dashboard = service.getDashboard("CUSTOM_RANGE", LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 4), 1, 1);

        assertEquals(new BigDecimal("150.00"), dashboard.deliveredOrderValue());
        assertEquals(2, dashboard.deliveredShopOrders());
        assertEquals(new BigDecimal("150.00"), dashboard.revenue().stream()
                .map(VietFreshHub.admin.dto.AdminDashboardData.RevenuePoint::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(List.of(100, 50), dashboard.revenue().stream()
                .map(VietFreshHub.admin.dto.AdminDashboardData.RevenuePoint::heightPercent).toList());
    }

    private void assertWindow(String period, LocalDate from, LocalDate to) {
        service.getDashboard(period, null, null, 1, 1);
        verify(orders).countOrdersByStatus(utcStart(from), utcStart(to.plusDays(1)));
        verify(deliveries).getDeliveredRevenue(utcStart(from), utcStart(to.plusDays(1)));
        verify(users).countNewCustomersByHour(utcStart(from), utcStart(to.plusDays(1)));
    }

    private LocalDateTime utcStart(LocalDate date) {
        return date.atStartOfDay(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private DeliveryRepository.RevenueByHour hour(int year, int month, int day, int hour, String amount) {
        var row = mock(DeliveryRepository.RevenueByHour.class);
        when(row.getDeliveryYear()).thenReturn(year);
        when(row.getDeliveryMonth()).thenReturn(month);
        when(row.getDeliveryDay()).thenReturn(day);
        when(row.getDeliveryHour()).thenReturn(hour);
        when(row.getRevenue()).thenReturn(new BigDecimal(amount));
        return row;
    }
}
