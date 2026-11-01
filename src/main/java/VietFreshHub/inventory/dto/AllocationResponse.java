package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.enums.AllocationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class AllocationResponse {
    private Long allocationId;
    private Long orderItemId;
    private String orderCode;
    private Integer quantity;
    private String unit;
    private AllocationStatus status;
    private LocalDateTime updatedAt;
    private List<ProcessingEvent> history;

    public String getStatusLabel() {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case RESERVED -> "Đang giữ hàng";
            case SOLD -> "Đã xuất kho";
            case RELEASED -> "Đã hủy giữ hàng";
        };
    }

    public String getStatusTone() {
        if (status == null) {
            return "vf-badge-gray";
        }
        return switch (status) {
            case RESERVED -> "vf-badge-yellow";
            case SOLD -> "vf-badge-green";
            case RELEASED -> "vf-badge-gray";
        };
    }

    @Getter
    @AllArgsConstructor
    public static class ProcessingEvent {
        private LocalDateTime occurredAt;
        private String description;
        private String note;
    }
}
