package VietFreshHub.Order.util;
import VietFreshHub.Order.entity.Order;
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
        case "FAILED" -> "Giao thất bại"; case "CANCELLED" -> "Giao hàng đã hủy"; default -> "Cập nhật giao hàng";
    }; }
    public static LocalDateTime local(LocalDateTime utc) { return utc==null ? null : LocalDateTime.ofInstant(utc.toInstant(ZoneOffset.UTC),ZoneId.of("Asia/Ho_Chi_Minh")); }
}
