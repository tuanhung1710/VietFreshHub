package VietFreshHub.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class IncomingOrderResponse {

    private Long shopOrderId;
    private String orderCode;
    private String customerName;
    private LocalDateTime placedAt;
    private BigDecimal totalAmount;
    private String orderStatus;
    private String orderStatusLabel;
    private String paymentStatus;
    public String getShopOrderCode() {
        return VietFreshHub.common.BusinessCodes.format("SOR", shopOrderId);
    }
}
