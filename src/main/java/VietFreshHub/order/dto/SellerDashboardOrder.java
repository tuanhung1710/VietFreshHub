package VietFreshHub.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SellerDashboardOrder(
        Long shopOrderId,
        String orderCode,
        String customerName,
        LocalDateTime placedAt,
        BigDecimal totalAmount,
        String status) {}
