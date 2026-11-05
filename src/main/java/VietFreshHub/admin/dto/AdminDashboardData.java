package VietFreshHub.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardData(
        LocalDate from,
        LocalDate to,
        long totalOrders,
        long pendingConfirmationOrders,
        long inDeliveryOrders,
        long completedOrders,
        long cancelledOrders,
        BigDecimal deliveredProductValueAfterDiscount,
        BigDecimal deliveredShippingFees,
        BigDecimal deliveredOrderValue,
        List<RevenuePoint> revenue,
        long totalProducts,
        List<BestSeller> bestSellers,
        long activeProducts,
        long inactiveProducts,
        long productsWithoutOrdersCount,
        List<NoOrderProduct> productsWithoutOrders,
        int productsWithoutOrdersPage,
        int productsWithoutOrdersPages) {

    public record RevenuePoint(LocalDate date, BigDecimal amount, int heightPercent) { }
    public record BestSeller(String productName, String unit, long unitsSold) { }
    public record NoOrderProduct(String productName, String shopName) { }
}
