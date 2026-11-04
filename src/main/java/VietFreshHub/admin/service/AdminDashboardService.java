package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminDashboardData;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PRODUCT_PAGE_SIZE = 10;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final DeliveryRepository deliveries;
    private final CatalogProductRepository catalogProducts;

    @Transactional(readOnly = true)
    public AdminDashboardData getDashboard(String period, LocalDate customFrom, LocalDate customTo,
                                           int productsWithoutOrdersPage) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate from;
        LocalDate to = today;
        switch (period == null ? "LAST_30_DAYS" : period) {
            case "TODAY" -> from = today;
            case "LAST_7_DAYS" -> from = today.minusDays(6);
            case "LAST_30_DAYS" -> from = today.minusDays(29);
            case "THIS_MONTH" -> from = today.withDayOfMonth(1);
            case "CUSTOM_RANGE" -> {
                if (customFrom == null || customTo == null || customFrom.isAfter(customTo)) {
                    throw new IllegalArgumentException("Vui lÃ²ng chá»n khoáº£ng ngÃ y há»£p lá»‡; ngÃ y báº¯t Ä‘áº§u khÃ´ng Ä‘Æ°á»£c sau ngÃ y káº¿t thÃºc.");
                }
                from = customFrom;
                to = customTo;
            }
            default -> throw new IllegalArgumentException("Khoáº£ng thá»i gian bÃ¡o cÃ¡o khÃ´ng há»£p lá»‡.");
        }
        if (productsWithoutOrdersPage < 1) {
            throw new IllegalArgumentException("Invalid product page.");
        }
        LocalDateTime fromUtc = from.atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var orderCounts = orders.countOrdersByStatus(fromUtc, toUtc);
        Map<Order.Status, Long> byStatus = new EnumMap<>(Order.Status.class);
        orderCounts.forEach(row -> byStatus.put(row.getStatus(), row.getOrderCount()));
        long startedPendingOrders = orders.countPendingOrdersWithStartedShopOrder(fromUtc, toUtc);
        long pendingConfirmationOrders = Math.max(0,
                byStatus.getOrDefault(Order.Status.PENDING, 0L) - startedPendingOrders);
        long inDeliveryOrders = byStatus.getOrDefault(Order.Status.CONFIRMED, 0L)
                + byStatus.getOrDefault(Order.Status.PROCESSING, 0L)
                + byStatus.getOrDefault(Order.Status.PARTIALLY_COMPLETED, 0L)
                + startedPendingOrders;
        long completedOrders = byStatus.getOrDefault(Order.Status.COMPLETED, 0L);
        long cancelledOrders = byStatus.getOrDefault(Order.Status.CANCELLED, 0L);
        long totalOrders = pendingConfirmationOrders + inDeliveryOrders + completedOrders + cancelledOrders;

        BigDecimal deliveredRevenue = deliveries.getDeliveredRevenue(fromUtc, toUtc);
        DeliveryRepository.DeliveredOrderTotals deliveredTotals = deliveries.getDeliveredOrderTotals(fromUtc, toUtc);
        Map<LocalDate, BigDecimal> dailyRevenue = new TreeMap<>();
        for (DeliveryRepository.RevenueByHour row : deliveries.getDeliveredRevenueByHour(fromUtc, toUtc)) {
            LocalDate localDate = LocalDateTime.of(row.getDeliveryYear(), row.getDeliveryMonth(),
                    row.getDeliveryDay(), row.getDeliveryHour(), 0).atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
            dailyRevenue.merge(localDate, row.getRevenue(), BigDecimal::add);
        }
        BigDecimal maxRevenue = dailyRevenue.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<AdminDashboardData.RevenuePoint> revenue = dailyRevenue.entrySet().stream()
                .map(entry -> {
                    int height = maxRevenue.signum() <= 0 ? 0 : entry.getValue()
                            .multiply(BigDecimal.valueOf(100)).divide(maxRevenue, 0, RoundingMode.HALF_UP).intValue();
                    return new AdminDashboardData.RevenuePoint(entry.getKey(), entry.getValue(), height);
                }).toList();

        List<AdminDashboardData.BestSeller> bestSellers = orderItems.findAdminBestSellers(fromUtc, toUtc,
                        PageRequest.of(0, 5)).stream()
                .map(row -> new AdminDashboardData.BestSeller(row.getProductName(), row.getUnit(), row.getUnitsSold()))
                .toList();
        var noOrders = catalogProducts.findProductsWithoutOrders(
                PageRequest.of(productsWithoutOrdersPage - 1, PRODUCT_PAGE_SIZE));
        var noOrderRows = noOrders.getContent().stream().map(row -> new AdminDashboardData.NoOrderProduct(
                row.getProductName(), row.getShopName())).toList();
        var productStatusCounts = catalogProducts.countProductsByStatus();
        long totalProducts = productStatusCounts.stream().mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).sum();
        long activeProducts = productStatusCounts.stream().filter(row -> "ACTIVE".equals(row.getStatus()))
                .mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).findFirst().orElse(0);
        long inactiveProducts = productStatusCounts.stream().filter(row -> "INACTIVE".equals(row.getStatus()))
                .mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).findFirst().orElse(0);

        return new AdminDashboardData(from, to, totalOrders, pendingConfirmationOrders, inDeliveryOrders,
                completedOrders, cancelledOrders,
                deliveredTotals == null || deliveredTotals.getProductValueAfterDiscount() == null
                        ? BigDecimal.ZERO : deliveredTotals.getProductValueAfterDiscount(),
                deliveredTotals == null || deliveredTotals.getShippingFees() == null
                        ? BigDecimal.ZERO : deliveredTotals.getShippingFees(),
                deliveredRevenue == null ? BigDecimal.ZERO : deliveredRevenue,
                revenue, totalProducts, bestSellers,
                activeProducts, inactiveProducts, noOrders.getTotalElements(), noOrderRows, productsWithoutOrdersPage,
                noOrders.getTotalPages());
    }

}
