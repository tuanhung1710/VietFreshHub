package VietFreshHub.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardData(
        LocalDate from,
        LocalDate to,
        long totalOrders,
        long deliveredOrders,
        long successfullyDeliveredOrders,
        BigDecimal deliveredOrderValue,
        long deliveredShopOrders,
        List<RevenuePoint> revenue,
        long totalProducts,
        List<BestSeller> bestSellers,
        long totalCustomers,
        long newCustomers,
        List<OrderStatusCount> orderStatuses,
        long activeProducts,
        long inactiveProducts,
        long productsWithoutOrdersCount,
        List<NoOrderProduct> productsWithoutOrders,
        int productsWithoutOrdersPage,
        int productsWithoutOrdersPages,
        List<InventoryQuantity> inventoryTotals,
        List<ShopInventory> inventoryByShop,
        List<ExpiringInventory> expiringInventory,
        long expiringInventoryTotal,
        int expiringInventoryPage,
        int expiringInventoryPages,
        long cancelledOrFailedOrders,
        List<OrderVolumePoint> orderVolume,
        long customersWithSuccessfulPurchase,
        long returningCustomers,
        List<CustomerCountPoint> newCustomerTrend) {

    public record RevenuePoint(LocalDate date, BigDecimal amount, int heightPercent) { }
    public record BestSeller(String productName, String unit, long unitsSold) { }
    public record OrderStatusCount(String status, long count) { }
    public record InventoryQuantity(String productName, String variantName, String unit,
                                    long quantityOnHand, long availableQuantity) { }
    public record ShopInventory(String shopName, String productName, String variantName, String unit,
                                long quantityOnHand, long availableQuantity) { }
    public record ExpiringInventory(String shopName, String productName, String variantName, String unit,
                                    LocalDate expiryDate, long availableQuantity) { }
    public record OrderVolumePoint(LocalDate date, long count) { }
    public record NoOrderProduct(String productName, String shopName) { }
    public record CustomerCountPoint(LocalDate date, long count) { }
}
