package VietFreshHub.order;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.service.DeliveryCreationService;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderDeliveryReadRepository;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.order.service.OrderLifecycleLock;
import VietFreshHub.order.service.OrderServiceImpl;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.delivery.service.DeliveryReturnQueryService;
import VietFreshHub.order.repository.OrderStatusHistoryRepository;
import VietFreshHub.order.service.OrderNotificationService;
import VietFreshHub.inventory.service.StockAllocationService;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerDashboardReportTest {
    @Test
    void dashboardOrdersUseAuthenticatedShopAndVietnamBusinessDay() {
        ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        ShopService shops = mock(ShopService.class);
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopId(authentication)).thenReturn(42L);
        when(shopOrders.findTop5ByShopIdAndOrder_PlacedAtGreaterThanEqualAndOrder_PlacedAtLessThanOrderByOrder_PlacedAtDesc(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        OrderServiceImpl service = service(shopOrders, orderItems, shops);

        assertEquals(List.of(), service.getTodaysOrders(authentication));
        assertEquals(List.of(), service.getBestSellingProducts(authentication));

        LocalDate day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime start = day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        verify(shopOrders).findTop5ByShopIdAndOrder_PlacedAtGreaterThanEqualAndOrder_PlacedAtLessThanOrderByOrder_PlacedAtDesc(
                42L, start, start.plusDays(1));
        verify(orderItems).findTopSellingProducts(42L, PageRequest.of(0, 5));
        verify(shops, org.mockito.Mockito.times(2)).getManagedShopId(authentication);
    }

    @Test
    void salesSummaryKeepsShopComponentsSeparateAndUsesLocalDayBoundary() {
        ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        ShopService shops = mock(ShopService.class);
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopId(authentication)).thenReturn(42L);
        ShopOrderRepository.SalesTotals totals = mock(ShopOrderRepository.SalesTotals.class);
        when(totals.getSalesAmount()).thenReturn(java.math.BigDecimal.valueOf(100));
        when(totals.getDiscounts()).thenReturn(java.math.BigDecimal.valueOf(20));
        when(totals.getShippingFees()).thenReturn(java.math.BigDecimal.valueOf(5));
        when(totals.getCompletedOrders()).thenReturn(1L);
        when(shopOrders.getCompletedSalesTotals(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(totals);
        OrderServiceImpl service = service(shopOrders, orderItems, shops);
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(new VietFreshHub.order.dto.ShopReportSummary.Sales(
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(20),
                java.math.BigDecimal.valueOf(5), 1L),
                service.getShopSalesSummary(authentication, day));

        LocalDateTime start = day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        verify(shopOrders).getCompletedSalesTotals(42L, start, start.plusDays(1));
        verify(shops).getManagedShopId(authentication);
        verify(orderItems, never()).findTopSellingProducts(42L, PageRequest.of(0, 5));
    }

    @Test
    void reportsUseManagedShopAndHalfOpenVietnamDateRange() {
        ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        ShopService shops = mock(ShopService.class);
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopId(authentication)).thenReturn(42L);
        ShopOrderRepository.SalesTotals totals = mock(ShopOrderRepository.SalesTotals.class);
        when(totals.getSalesAmount()).thenReturn(java.math.BigDecimal.ZERO);
        when(totals.getDiscounts()).thenReturn(java.math.BigDecimal.ZERO);
        when(totals.getShippingFees()).thenReturn(java.math.BigDecimal.ZERO);
        when(totals.getCompletedOrders()).thenReturn(0L);
        when(shopOrders.getCompletedSalesTotals(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(totals);
        when(shopOrders.getOrderCountsByStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(shopOrders.countDistinctCustomers(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(orderItems.findTopSellingProductsInPeriod(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        OrderServiceImpl service = service(shopOrders, orderItems, shops);
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 7);
        LocalDateTime start = from.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        var report = service.getShopReport(authentication, from, to);

        assertEquals(from, report.from());
        assertEquals(to, report.to());
        verify(shopOrders).getCompletedSalesTotals(42L, start, end);
        verify(shopOrders).getOrderCountsByStatus(42L, start, end);
        verify(shopOrders).countDistinctCustomers(42L, start, end);
        verify(orderItems).findTopSellingProductsInPeriod(42L, start, end, PageRequest.of(0, 10));
        verify(shops).getManagedShopId(authentication);
    }

    private OrderServiceImpl service(ShopOrderRepository shopOrders, OrderItemRepository orderItems,
                                     ShopService shops) {
        return new OrderServiceImpl(shopOrders, mock(OrderAddressRepository.class),
                mock(OrderDeliveryReadRepository.class), orderItems, mock(OrderStatusHistoryRepository.class), shops,
                mock(PaymentService.class), mock(DeliveryCreationService.class), mock(DeliveryReturnQueryService.class),
                mock(OrderNotificationService.class), mock(StockAllocationService.class), mock(AuthService.class), mock(EntityManager.class),
                mock(OrderLifecycleLock.class));
    }
}
