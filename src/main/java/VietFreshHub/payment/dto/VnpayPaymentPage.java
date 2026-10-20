package VietFreshHub.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VnpayPaymentPage(Long orderId, String orderCode, BigDecimal amount, String status,
                               String message, LocalDateTime expiresAt, boolean canPay) { }
