package VietFreshHub.payout.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPayoutRow(
        Long payoutId,
        Long shopId,
        String shopName,
        Long shopOrderId,
        String orderCode,
        String fundsSource,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String status,
        LocalDateTime eligibleAt,
        LocalDateTime fundsConfirmedAt,
        boolean hasVerifiedAccount,
        boolean canManualBatch
) {}
