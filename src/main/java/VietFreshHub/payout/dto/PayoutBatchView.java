package VietFreshHub.payout.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PayoutBatchView(
        Long batchId,
        String scheduledSlot,
        LocalDateTime scheduledAt,
        BigDecimal totalAmount,
        int payoutCount,
        int shopCount,
        String status,
        List<PayoutBatchShopTransfer> shopTransfers
) {}
