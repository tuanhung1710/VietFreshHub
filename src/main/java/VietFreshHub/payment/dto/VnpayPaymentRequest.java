package VietFreshHub.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** All values must come from a server-side payment attempt, never client totals. */
public record VnpayPaymentRequest(String reference, BigDecimal amount, String clientIp,
                                  Instant createdAt, Instant expiresAt) { }
