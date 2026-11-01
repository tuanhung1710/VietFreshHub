package VietFreshHub.order.service;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public final class OrderLabels {
    private OrderLabels() {}
    public static String shop(String status) { return switch(status) {
        case "PENDING" -> "Chờ cửa hàng xác nhận"; case "CONFIRMED" -> "Đã xác nhận"; case "PREPARING" -> "Đang chuẩn bị";
        case "READY_FOR_DELIVERY" -> "Sẵn sàng giao"; case "OUT_FOR_DELIVERY" -> "Đang giao"; case "COMPLETED" -> "Hoàn thành";
        case "CANCELLED" -> "Đã hủy"; default -> "Cập nhật đơn hàng";
    }; }
    public static String delivery(String status) { return switch(status) {
        case "UNASSIGNED" -> "Chờ phân công giao hàng"; case "ASSIGNED" -> "Đã phân công"; case "ACCEPTED" -> "Nhân viên đã nhận nhiệm vụ";
        case "PICKED_UP" -> "Đã lấy hàng"; case "OUT_FOR_DELIVERY" -> "Đang giao"; case "DELIVERED" -> "Giao hàng thành công";
        case "REDELIVERY_SCHEDULED" -> "Đã hẹn giao lại"; case "FAILED" -> "Giao thất bại"; case "CANCELLED" -> "Giao hàng đã hủy"; default -> "Cập nhật giao hàng";
    }; }
    public static String customerStatus(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "Chờ cửa hàng xác nhận đơn hàng";
            case "CONFIRMED" -> "Cửa hàng đã xác nhận đơn hàng";
            case "PREPARING" -> "Cửa hàng đang chuẩn bị đơn hàng";
            case "READY_FOR_DELIVERY" -> "Sẵn sàng giao hàng";
            case "ACCEPTED" -> "Nhân viên đã nhận nhiệm vụ";
            case "PICKED_UP" -> "Nhân viên giao hàng đã lấy hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "COMPLETED" -> "Hoàn thành";
            case "FAILED" -> "Giao hàng thất bại";
            case "REDELIVERY_SCHEDULED" -> "Đã hẹn giao lại";
            case "CANCELLED" -> "Đã hủy đơn hàng";
            default -> "Thông tin giao hàng cần được kiểm tra.";
        };
    }

    public static Map<String, String> customerStatusOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "CONFIRMED", "PREPARING", "READY_FOR_DELIVERY", "ACCEPTED",
                "PICKED_UP", "OUT_FOR_DELIVERY", "COMPLETED", "FAILED", "CANCELLED")) {
            options.put(status, customerStatus(status));
        }
        return options;
    }
    public static String payment(String status) { return switch(status==null ? "" : status) {
        case "UNPAID" -> "Chưa thu tiền"; case "PENDING" -> "Đang chờ thanh toán"; case "PAID" -> "Đã thanh toán";
        case "PARTIALLY_REFUNDED" -> "Hoàn tiền một phần"; case "REFUNDED" -> "Đã hoàn tiền";
        case "FAILED" -> "Thanh toán thất bại"; default -> "Cập nhật thanh toán";
    }; }
    public static Map<String, String> shopPaymentStatusOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (String status : List.of("UNPAID", "PAID")) {
            options.put(status, payment(status));
        }
        return options;
    }
    public static String customerPayment(String shopStatus, String paymentStatus, boolean cod) {
        return cod && "CANCELLED".equals(shopStatus) && "UNPAID".equals(paymentStatus)
                ? "Không phát sinh thanh toán" : payment(paymentStatus);
    }
    public static String paymentTransaction(VietFreshHub.payment.entity.Payment.Status status) { return switch(status) {
        case PENDING -> "Chờ thanh toán"; case PROCESSING -> "Đang xử lý thanh toán"; case PAID -> "Đã thanh toán";
        case FAILED -> "Thanh toán thất bại"; case CANCELLED -> "Đã hủy giao dịch"; case REFUNDED -> "Đã hoàn tiền";
        case PARTIALLY_REFUNDED -> "Hoàn tiền một phần";
    }; }
    public static LocalDateTime local(LocalDateTime utc) { return utc==null ? null : LocalDateTime.ofInstant(utc.toInstant(ZoneOffset.UTC),ZoneId.of("Asia/Ho_Chi_Minh")); }
}
