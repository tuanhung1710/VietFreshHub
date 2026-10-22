package VietFreshHub.inventory.dto;

/** Counts lots rather than adding quantities that can have different sale units. */
public record DisposalResult(int disposedBatchCount, int reservedBatchCount) {
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
