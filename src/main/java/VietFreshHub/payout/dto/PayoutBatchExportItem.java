package VietFreshHub.payout.dto;

import java.math.BigDecimal;

public record PayoutBatchExportItem(
        Long shopId,
        String shopName,
        String bankName,
        String bankCode,
        String accountNumber,
        String accountHolder,
        Long shopOrderId,
        String orderCode,
        BigDecimal subtotalAmount,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal commissionAmount,
        BigDecimal transferAmount,
        String status,
        String transferReference
) {}
