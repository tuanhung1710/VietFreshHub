package VietFreshHub.order.dto;

import VietFreshHub.order.repository.OrderItemRepository.BestSellingProduct;
import VietFreshHub.order.repository.ShopOrderRepository.StatusCount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ShopReportSummary(
        LocalDate from,
        LocalDate to,
        Sales sales,
        List<StatusCount> ordersByStatus,
        long distinctCustomers,
        List<BestSellingProduct> bestSellingProducts
) {
    public record Sales(BigDecimal productSubtotal, BigDecimal discounts,
                        BigDecimal shippingFees, Long completedOrders) {}
}
