package VietFreshHub.delivery.dto;

import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryHistoryRow(Long assignmentId, Long deliveryId, String orderCode, Long shopId,
                                 String shopName, String recipientName, String recipientPhone,
                                 String deliveryAddress, String outcome, BigDecimal totalAmount,
                                 LocalDateTime resultAt, String paymentName, String returnState,
                                 boolean attributionVerified) {
    public LocalDateTime getLocalResultAt() {
        return OrderLabels.local(resultAt);
    }

    public String getOutcomeLabel() {
        if ("FAILED".equals(outcome)) {
            return "Giao thất bại";
        }
        return attributionVerified ? "Giao thành công"
                : "Hồ sơ giao thành công; chưa xác định chắc chắn người hoàn tất";
    }

    public String getReturnLabel() {
        return switch (returnState) {
            case "PENDING" -> "Đang chờ cửa hàng nhận lại hàng";
            case "CONFIRMED" -> "Cửa hàng đã xác nhận nhận lại hàng";
            case "UNKNOWN" -> "Chưa có dữ liệu xác nhận trả hàng phù hợp";
            default -> "Không áp dụng";
        };
    }
}
