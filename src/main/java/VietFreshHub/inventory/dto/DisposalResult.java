package VietFreshHub.inventory.dto;

/** Thống kê số lô, tránh cộng số lượng hàng có thể dùng các đơn vị bán khác nhau. */
public record DisposalResult(int disposedBatchCount, int reservedBatchCount) {
    /** Tạo thông báo số lô đã hủy và nhắc xử lý các lô còn hàng giữ cho đơn. */
    public String getMessage() {
        String message = disposedBatchCount > 0
                ? "Đã hủy toàn bộ hàng hết hạn chưa giữ cho đơn trong " + disposedBatchCount + " lô."
                : "Không còn hàng hết hạn chưa giữ cho đơn để hủy.";
        if (reservedBatchCount > 0) {
            message += " Còn " + reservedBatchCount
                    + " lô có hàng đang giữ cho đơn; cần xử lý đơn hàng trước khi hủy phần còn lại.";
        }
        return message;
    }
}
