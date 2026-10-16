package VietFreshHub.product.dto;

public record ProductManagementState(String code, String reason, boolean canOpen, boolean canHide,
        String openBlockedReason) {
    public ProductManagementState(String code, String reason) {
        this(code, reason, false, false, "");
    }
    public String getCode() { return code; }
    public String getReason() { return reason; }
    public boolean isCanOpen() { return canOpen; }
    public boolean isCanHide() { return canHide; }
    public String getOpenBlockedReason() { return openBlockedReason; }
    public String getLabel() {
        return switch (code) {
            case "active" -> "Đang hoạt động";
            case "out-of-stock" -> "Hết hàng";
            case "violation" -> "Vi phạm";
            case "hidden" -> "Đã ẩn";
            case "archived" -> "Ngừng kinh doanh";
            default -> "Chưa thể đặt mua";
        };
    }
}
