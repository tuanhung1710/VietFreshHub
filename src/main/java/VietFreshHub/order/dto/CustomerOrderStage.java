package VietFreshHub.order.dto;

/** Customer-facing order stages; payment transaction states remain unchanged. */
public enum CustomerOrderStage {
    WAITING_PAYMENT("Chờ thanh toán"),
    WAITING_CONFIRMATION("Chờ xác nhận"),
    PREPARING("Đang chuẩn bị"),
    SHIPPING("Vận chuyển"),
    AWAITING_DELIVERY("Chờ giao hàng"),
    COMPLETED("Hoàn thành"),
    CANCELLED("Đã hủy");

    private final String label;
    CustomerOrderStage(String label) { this.label = label; }
    public String label() { return label; }
}
