package VietFreshHub.admin.repository;

import java.time.LocalDateTime;

public interface BulkApproveOperation {

    int bulkApprovePending(
            String keyword,
            LocalDateTime fromDate,
            LocalDateTime untilDate,
            Long adminId,
            LocalDateTime reviewedAt);
}
