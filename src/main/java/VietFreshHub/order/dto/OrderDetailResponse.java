package VietFreshHub.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderDetailResponse {

    private String orderCode;
    private LocalDateTime placedAt;
    private String customerName;
    private String recipientName;
    private String phone;
    private String deliveryAddress;
    private String orderStatus;
    private String orderStatusLabel;
    private String paymentStatusLabel;
    private String paymentStatus;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    private String shopName;
    private String shopPhone;
    private String shopAddress;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private String paymentMethodLabel;
    private String totalAmountInWords;
    private LocalDateTime invoicePlacedAt;
}
