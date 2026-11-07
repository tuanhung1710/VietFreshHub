package VietFreshHub.inventory.service;

import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.AuditResponse;
import VietFreshHub.inventory.dto.BatchMetadataRequest;
import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.DisposalResult;
import VietFreshHub.inventory.dto.StockChangeRequest;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface InventoryService {
    /** Nhập lô hàng mới cho phân loại thuộc cửa hàng và ghi lịch sử kho. */
    void restock(Authentication authentication, Long variantId, BatchRequest form);

    /** Chuẩn bị biểu mẫu sửa thông tin lô hiện có. */
    BatchMetadataRequest batchForm(Authentication authentication, Long batchId);

    /** Sửa ngày nhập, hạn sử dụng và giá vốn của lô, giữ nguyên số lượng. */
    void updateBatch(Authentication authentication, Long batchId, BatchMetadataRequest form);

    /** Điều chỉnh tồn thực tế của lô mà vẫn bảo toàn phần hàng đã giữ cho đơn. */
    void adjust(Authentication authentication, Long batchId, StockChangeRequest form);

    /** Hủy toàn bộ hàng hết hạn chưa giữ cho đơn của một lô và trả kết quả theo số lô. */
    DisposalResult dispose(Authentication authentication, Long batchId);

    /** Hủy hàng hết hạn chưa giữ cho đơn trên mọi phân loại của sản phẩm. */
    DisposalResult disposeExpiredProduct(Authentication authentication, Long productId);

    /** Khóa hoặc mở khóa lô với lý do, giữ nguyên lượng đã giữ cho đơn. */
    void block(Authentication authentication, Long batchId, boolean blocked, String reason);

    /** Lọc lịch sử hoạt động và bổ sung tên đối tượng, người thao tác để hiển thị. */
    Page<AuditResponse> activityHistory(
            Authentication authentication, ActivityHistoryFilter filters, int page, int size);

    /** Lấy chi tiết lô thuộc cửa hàng, kèm tồn kho, hạn sử dụng và phân bổ cho đơn. */
    BatchResponse batch(Authentication authentication, Long batchId);
}
