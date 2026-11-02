package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminDashboardData;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
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
    private static final int EXPIRY_PAGE_SIZE = 10;
    private static final int PRODUCT_PAGE_SIZE = 10;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final DeliveryRepository deliveries;
    private final CatalogProductRepository catalogProducts;
    private final UserRepository users;
    private final InventoryBatchRepository inventory;

    @Transactional(readOnly = true)
    public AdminDashboardData getDashboard(String period, LocalDate customFrom, LocalDate customTo,
                                           int expiringPage, int productsWithoutOrdersPage) {
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
        if (expiringPage < 1) {
            throw new IllegalArgumentException("Invalid inventory page.");
        }
        if (productsWithoutOrdersPage < 1) {
            throw new IllegalArgumentException("Invalid product page.");
        }
        LocalDateTime fromUtc = from.atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var orderCounts = orders.countOrdersByStatus(fromUtc, toUtc);
        Map<Order.Status, Long> byStatus = new EnumMap<>(Order.Status.class);
        orderCounts.forEach(row -> byStatus.put(row.getStatus(), row.getOrderCount()));
        long totalOrders = orderCounts.stream().mapToLong(OrderRepository.StatusCount::getOrderCount).sum();

        var revenueTotal = deliveries.getDeliveredRevenue(fromUtc, toUtc);
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
                        PageRequest.of(0, 10)).stream()
                .map(row -> new AdminDashboardData.BestSeller(row.getProductName(), row.getUnit(), row.getUnitsSold()))
                .toList();
        List<AdminDashboardData.OrderStatusCount> statuses = java.util.Arrays.stream(Order.Status.values())
                .map(status -> new AdminDashboardData.OrderStatusCount(status.name(), byStatus.getOrDefault(status, 0L)))
                .toList();

        Map<LocalDate, Long> volumeByDate = new TreeMap<>();
        for (OrderRepository.VolumeByHour row : orders.findOrderVolumeByHour(fromUtc, toUtc)) {
            LocalDate localDate = LocalDateTime.of(row.getOrderYear(), row.getOrderMonth(), row.getOrderDay(),
                    row.getOrderHour(), 0).atZone(ZoneOffset.UTC).withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
            volumeByDate.merge(localDate, row.getOrderCount(), Long::sum);
        }
        List<AdminDashboardData.OrderVolumePoint> orderVolume = volumeByDate.entrySet().stream()
                .map(entry -> new AdminDashboardData.OrderVolumePoint(entry.getKey(), entry.getValue())).toList();
        Map<LocalDate, Long> newCustomersByDate = new TreeMap<>();
        for (UserRepository.NewCustomersByHour row : users.countNewCustomersByHour(fromUtc, toUtc)) {
            LocalDate localDate = LocalDateTime.of(row.getCustomerYear(), row.getCustomerMonth(), row.getCustomerDay(),
                    row.getCustomerHour(), 0).atZone(ZoneOffset.UTC).withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
            newCustomersByDate.merge(localDate, row.getCustomerCount(), Long::sum);
        }
        List<AdminDashboardData.CustomerCountPoint> newCustomerTrend = newCustomersByDate.entrySet().stream()
                .map(entry -> new AdminDashboardData.CustomerCountPoint(entry.getKey(), entry.getValue())).toList();
        long newCustomers = newCustomerTrend.stream().mapToLong(AdminDashboardData.CustomerCountPoint::count).sum();
        var inventoryTotals = inventory.summarizeByProductVariant(today).stream()
                .map(row -> new AdminDashboardData.InventoryQuantity(row.getProductName(), row.getVariantName(),
                        row.getUnit(), row.getQuantityOnHand(),
                        row.getAvailableQuantity())).toList();
        var inventoryByShop = inventory.summarizeByShopAndUnit(today).stream()
                .map(row -> new AdminDashboardData.ShopInventory(row.getShopName(), row.getProductName(),
                        row.getVariantName(), row.getUnit(),
                        row.getQuantityOnHand(), row.getAvailableQuantity())).toList();
        var expiring = inventory.findApproachingExpiry(today, PageRequest.of(expiringPage - 1, EXPIRY_PAGE_SIZE));
        var expiringRows = expiring.getContent().stream().map(row -> new AdminDashboardData.ExpiringInventory(
                row.getShopName(), row.getProductName(), row.getVariantName(), row.getUnit(), row.getExpiryDate(),
                row.getAvailableQuantity())).toList();
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

        return new AdminDashboardData(from, to, totalOrders,
                byStatus.getOrDefault(Order.Status.COMPLETED, 0L),
                deliveries.countSuccessfullyDeliveredOrders(fromUtc, toUtc),
                revenueTotal.getRevenue() == null ? BigDecimal.ZERO : revenueTotal.getRevenue(),
                revenueTotal.getDeliveredShopOrders() == null ? 0 : revenueTotal.getDeliveredShopOrders(),
                revenue, totalProducts, bestSellers,
                users.countCustomers(),
                newCustomers, statuses,
                activeProducts, inactiveProducts, noOrders.getTotalElements(), noOrderRows, productsWithoutOrdersPage,
                noOrders.getTotalPages(), inventoryTotals, inventoryByShop, expiringRows,
                expiring.getTotalElements(), expiringPage, expiring.getTotalPages(),
                deliveries.countCancelledOrFailedOrders(fromUtc, toUtc), orderVolume,
                users.countCustomersWithSuccessfulPurchase(), users.countReturningCustomers(), newCustomerTrend);
    }

}
