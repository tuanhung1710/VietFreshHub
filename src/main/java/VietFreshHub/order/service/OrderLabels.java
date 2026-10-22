package VietFreshHub.order.service;
import VietFreshHub.order.entity.Order;
import java.time.*;
public final class OrderLabels {
    private OrderLabels() {}
    public static String order(Order.Status status) { return switch(status) {
        case PENDING -> "Chờ cửa hàng xác nhận"; case CONFIRMED -> "Đã xác nhận"; case PROCESSING -> "Đang xử lý";
        case PARTIALLY_COMPLETED -> "Hoàn thành một phần"; case COMPLETED -> "Hoàn thành"; case CANCELLED -> "Đã hủy";
    }; }
    public static String shop(String status) { return switch(status) {
        case "PENDING" -> "Chờ cửa hàng xác nhận"; case "CONFIRMED" -> "Đã xác nhận"; case "PREPARING" -> "Đang chuẩn bị";
        case "READY_FOR_DELIVERY" -> "Sẵn sàng giao"; case "OUT_FOR_DELIVERY" -> "Đang giao"; case "COMPLETED" -> "Hoàn thành";
        case "CANCELLED" -> "Đã hủy"; default -> "Cập nhật đơn hàng";
    }; }
    public static String delivery(String status) { return switch(status) {
        case "UNASSIGNED" -> "Chờ phân công giao hàng"; case "ASSIGNED" -> "Đã phân công"; case "ACCEPTED" -> "Nhân viên đã nhận nhiệm vụ";
        case "PICKED_UP" -> "Đã lấy hàng"; case "OUT_FOR_DELIVERY" -> "Đang giao"; case "DELIVERED" -> "Giao thành công";
        case "REDELIVERY_SCHEDULED" -> "Đã hẹn giao lại"; case "FAILED" -> "Giao thất bại"; case "CANCELLED" -> "Giao hàng đã hủy"; default -> "Cập nhật giao hàng";
    }; }
    public static String payment(String status) { return switch(status==null ? "" : status) {
        case "UNPAID" -> "Chưa thu tiền"; case "PENDING" -> "Đang chờ thanh toán"; case "PAID" -> "Đã thanh toán";
        case "PARTIALLY_REFUNDED" -> "Hoàn tiền một phần"; case "REFUNDED" -> "Đã hoàn tiền";
        case "FAILED" -> "Thanh toán thất bại"; default -> "Cập nhật thanh toán";
    }; }
    public static String paymentTransaction(VietFreshHub.payment.entity.Payment.Status status) { return switch(status) {
        case PENDING -> "Chờ thanh toán"; case PROCESSING -> "Đang xử lý"; case PAID -> "Đã thanh toán";
        case FAILED -> "Thanh toán thất bại"; case CANCELLED -> "Đã hủy giao dịch"; case REFUNDED -> "Đã hoàn tiền";
        case PARTIALLY_REFUNDED -> "Hoàn tiền một phần";
    }; }
    public static String paymentTransaction(VietFreshHub.payment.entity.Payment.Status status, String methodCode) {
        return status == VietFreshHub.payment.entity.Payment.Status.PENDING && "COD".equals(methodCode)
                ? "Chờ thu COD" : paymentTransaction(status);
    }
    public static LocalDateTime local(LocalDateTime utc) { return utc==null ? null : LocalDateTime.ofInstant(utc.toInstant(ZoneOffset.UTC),ZoneId.of("Asia/Ho_Chi_Minh")); }
}
