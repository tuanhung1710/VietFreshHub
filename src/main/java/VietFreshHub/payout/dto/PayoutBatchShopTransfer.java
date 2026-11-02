package VietFreshHub.payout.dto;

import java.math.BigDecimal;

public record PayoutBatchShopTransfer(
        String shopName,
        long orderCount,
        BigDecimal amount
) {}
