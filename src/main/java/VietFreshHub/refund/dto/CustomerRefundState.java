package VietFreshHub.refund.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerRefundState(Long orderId, String orderCode, BigDecimal refundAmount,
                                  boolean eligible, LocalDateTime submittedAt, String unavailableReason) {}
