package VietFreshHub.Order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ProcessedOrderResponse {

    private Long shopOrderId;
    private String orderCode;
    private String customerName;
    private LocalDateTime placedAt;
    private BigDecimal totalAmount;
    private String orderStatusLabel;
    private String paymentStatus;
}
