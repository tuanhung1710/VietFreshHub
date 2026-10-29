package VietFreshHub.payout.dto;

import java.math.BigDecimal;

public record PayoutBatchShopTransfer(
        Long shopId,
        String shopName,
        BigDecimal amount,
        long orderCount,
        String status,
        String transferReference
) {}
