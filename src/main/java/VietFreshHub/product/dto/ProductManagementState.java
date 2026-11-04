package VietFreshHub.product.dto;

public record ProductManagementState(
        String code, String reason, boolean canOpen, boolean canHide, String openBlockedReason) {
    /** Tạo trạng thái quản lý cơ bản với các thao tác mở bán, ẩn đều chưa được phép. */
    public ProductManagementState(String code, String reason) {
        this(code, reason, false, false, "");
    }

    /** Cung cấp mã trạng thái theo quy ước getter để dùng trong biểu mẫu Thymeleaf. */
    public String getCode() {
        return code;
    }

    /** Cung cấp lý do trạng thái theo quy ước getter của giao diện. */
    public String getReason() {
        return reason;
    }

    /** Cho biết giao diện có thể hiển thị thao tác mở bán cho sản phẩm. */
    public boolean isCanOpen() {
        return canOpen;
    }

    /** Cho biết giao diện có thể hiển thị thao tác tạm ẩn sản phẩm. */
    public boolean isCanHide() {
        return canHide;
    }

    /** Lấy lý do không cho mở bán để giải thích cho người quản lý. */
    public String getOpenBlockedReason() {
        return openBlockedReason;
    }

    /** Đổi mã trạng thái quản lý thành nhãn tiếng Việt để hiển thị. */
    public String getLabel() {
        return switch (code) {
            case "active" -> "Đang bán";
            case "out-of-stock" -> "Hết hàng";
            case "violation" -> "Vi phạm";
            case "hidden" -> "Tạm ẩn";
            case "archived" -> "Đã xóa";
            default -> "Chưa thể đặt mua";
        };
    }
}
