package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.enums.BatchStatus;

import lombok.Getter;

import java.util.List;

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
    @lombok.Setter
    private String variantName;
    @lombok.Setter
    private java.util.Map<Long, String> allocationOrderCodes = java.util.Map.of();

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

    public BatchResponse(
            InventoryBatch batch, int available, boolean expired, boolean nearExpiry,
            BatchMarkdown markdown, List<StockAllocation> allocations, String productName,
            String sku, String unit, boolean markdownEligible, int expiryWarningDays,
            Long daysUntilExpiry) {
        this(batch, available, expired, nearExpiry, markdown, allocations, productName,
                sku, unit, markdownEligible, expiryWarningDays, daysUntilExpiry, false);
    }

    public boolean isEmptyStock() {
        return Integer.valueOf(0).equals(batch.getQuantityOnHand())
                && Integer.valueOf(0).equals(batch.getReservedQuantity())
                && available == 0;
    }

    public boolean isFullyAllocated() {
        return batch.getStatus() == BatchStatus.ACTIVE
                && !expired
                && batch.getQuantityOnHand() != null
                && batch.getQuantityOnHand() > 0
                && batch.getQuantityOnHand().equals(batch.getReservedQuantity());
    }

    public String getStateLabel() {
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
            return Long.valueOf(0).equals(daysUntilExpiry)
                    ? "Hết hạn hôm nay"
                    : "Sắp hết hạn · Còn " + daysUntilExpiry + " ngày";
        }
        return "Còn hàng";
    }

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

    public boolean isCanAdjust() {
        return !expired && batch.getStatus() == BatchStatus.ACTIVE;
    }

    public boolean isCanEdit() {
        return true;
    }

    public boolean isCanEditFromList() {
        return isCanAdjust() && batch.getQuantityOnHand() != null
                && batch.getQuantityOnHand() > 0 && !isFullyAllocated();
    }

    public int getDisposableQuantity() {
        return VietFreshHub.inventory.rule.InventoryRules.disposableQuantity(
                batch, VietFreshHub.inventory.rule.InventoryRules.today());
    }

    public boolean isCanDispose() {
        return getDisposableQuantity() > 0;
    }

    public boolean isCanBlock() {
        return isCanEditFromList();
    }

    public boolean isCanUnblock() {
        return !expired && batch.getStatus() == BatchStatus.BLOCKED;
    }

    public boolean isCanMarkdown() {
        return markdownEligible && markdownAllowed && !isEmptyStock() && !isFullyAllocated()
                && batch.getQuantityOnHand() != null && batch.getQuantityOnHand() > 0;
    }
}
