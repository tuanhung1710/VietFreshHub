package VietFreshHub.payout.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PayoutBatchExport(
        Long batchId,
        String scheduledSlot,
        LocalDateTime scheduledAt,
        BigDecimal totalAmount,
        List<PayoutBatchExportItem> items
) {}
