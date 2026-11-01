package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.rule.InventoryRules;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
public class BatchResponse {

    private InventoryBatch batch;
    private int available;
    private boolean expired;
    private boolean nearExpiry;
    private BatchMarkdown markdown;
    private List<StockAllocation> allocations;
    private String productName;
    private String sku;
    private String unit;
    private boolean markdownEligible;
    private int expiryWarningDays;
    private Long daysUntilExpiry;
    private boolean markdownAllowed;
    @Setter
    private String variantName;
    @Setter
    private Map<Long, String> allocationOrderCodes = Map.of();
    @Setter
    private List<AllocationResponse> allocationDetails = List.of();

    /** Tạo dữ liệu hiển thị lô với tồn, hạn sử dụng, phân bổ và điều kiện cho phép giảm giá. */
    @Builder
    public BatchResponse(
            InventoryBatch batch, int available, boolean expired, boolean nearExpiry,
            BatchMarkdown markdown, List<StockAllocation> allocations, String productName,
            String sku, String unit, boolean markdownEligible, int expiryWarningDays,
            Long daysUntilExpiry, boolean markdownAllowed) {
        this.batch = batch;
        this.available = available;
        this.expired = expired;
        this.nearExpiry = nearExpiry;
        this.markdown = markdown;
        this.allocations = allocations;
        this.productName = productName;
        this.sku = sku;
        this.unit = unit;
        this.markdownEligible = markdownEligible;
        this.expiryWarningDays = expiryWarningDays;
        this.daysUntilExpiry = daysUntilExpiry;
        this.markdownAllowed = markdownAllowed;
    }

    /**
     * Tạo dữ liệu lô khi chưa có kết quả kiểm tra điều kiện kinh doanh; mặc định chưa cho giảm giá.
     */
    public BatchResponse(
            InventoryBatch batch, int available, boolean expired, boolean nearExpiry,
            BatchMarkdown markdown, List<StockAllocation> allocations, String productName,
            String sku, String unit, boolean markdownEligible, int expiryWarningDays,
            Long daysUntilExpiry) {
        this(batch, available, expired, nearExpiry, markdown, allocations, productName,
                sku, unit, markdownEligible, expiryWarningDays, daysUntilExpiry, false);
    }

    /** Xác định lô đã hết hàng khi cả tồn thực tế, lượng giữ và lượng khả dụng đều bằng 0. */
    public boolean isEmptyStock() {
        return Integer.valueOf(0).equals(batch.getQuantityOnHand())
                && Integer.valueOf(0).equals(batch.getReservedQuantity())
                && available == 0;
    }

    /** Xác định lô đang hoạt động, còn hạn và toàn bộ tồn thực tế đã được giữ cho đơn. */
    public boolean isFullyAllocated() {
        return batch.getStatus() == BatchStatus.ACTIVE
                && !expired
                && batch.getQuantityOnHand() != null
                && batch.getQuantityOnHand() > 0
                && batch.getQuantityOnHand().equals(batch.getReservedQuantity());
    }

    /**
     * Chọn nhãn trạng thái theo ưu tiên: hết hàng, hết hạn, khóa, phân bổ hết, cận hạn rồi còn hàng.
     */
    public String getStateLabel() {
        // Các trạng thái có thể cùng đúng; nhánh đứng trước quyết định nhãn ưu tiên trên giao diện.
        if (isEmptyStock()) {
            return "Đã hết hàng";
        }
        if (expired) {
            return "Hết hạn sử dụng";
        }
        if (batch.getStatus() == BatchStatus.BLOCKED) {
            return "Đang khóa";
        }
        if (isFullyAllocated()) {
            return "Đã phân bổ hết";
        }
        if (nearExpiry) {
            // Ngay ngày hết hạn vẫn được xem là còn hạn, nhưng cần nhãn cảnh báo riêng.
            return Long.valueOf(0).equals(daysUntilExpiry)
                    ? "Hết hạn hôm nay"
                    : "Sắp hết hạn · Còn " + daysUntilExpiry + " ngày";
        }
        return "Còn hàng";
    }

    /** Chọn màu nhãn theo tình trạng lô, ưu tiên hết hàng, hết hạn và khóa hoặc phân bổ hết. */
    public String getStateTone() {
        if (isEmptyStock()) {
            return "vf-badge-gray";
        }
        if (expired) {
            return "vf-badge-red";
        }
        if (batch.getStatus() == BatchStatus.BLOCKED || isFullyAllocated()) {
            return "vf-badge-gray";
        }
        return nearExpiry ? "vf-badge-yellow" : "vf-badge-green";
    }

    /** Cho phép hiện thao tác điều chỉnh khi lô đang hoạt động và chưa hết hạn. */
    public boolean isCanAdjust() {
        return !expired && batch.getStatus() == BatchStatus.ACTIVE;
    }

    /** Luôn cho mở trang sửa thông tin lô; service sẽ kiểm tra riêng điều kiện của dữ liệu mới. */
    public boolean isCanEdit() {
        return true;
    }

    /** Cho sửa nhanh từ danh sách khi lô hoạt động, còn hạn, còn tồn và chưa phân bổ hết. */
    public boolean isCanEditFromList() {
        return isCanAdjust() && batch.getQuantityOnHand() != null
                && batch.getQuantityOnHand() > 0 && !isFullyAllocated();
    }

    /** Tính lượng hàng hết hạn có thể hủy theo ngày hiện tại, giữ lại phần đã giữ cho đơn. */
    public int getDisposableQuantity() {
        return InventoryRules.disposableQuantity(
                batch, InventoryRules.today());
    }

    /** Cho hiện thao tác hủy khi lô có hàng hết hạn chưa giữ cho đơn. */
    public boolean isCanDispose() {
        return getDisposableQuantity() > 0;
    }

    /** Cho hiện thao tác khóa lô khi đủ điều kiện thao tác nhanh từ danh sách. */
    public boolean isCanBlock() {
        return isCanEditFromList();
    }

    /** Cho hiện thao tác mở khóa khi lô đang khóa và chưa hết hạn. */
    public boolean isCanUnblock() {
        return !expired && batch.getStatus() == BatchStatus.BLOCKED;
    }

    /**
     * Cho hiện thao tác giảm giá khi lô và điều kiện kinh doanh đều hợp lệ, còn hàng chưa phân bổ.
     */
    public boolean isCanMarkdown() {
        return markdownEligible && markdownAllowed && !isEmptyStock() && !isFullyAllocated()
                && batch.getQuantityOnHand() != null && batch.getQuantityOnHand() > 0;
    }
}
