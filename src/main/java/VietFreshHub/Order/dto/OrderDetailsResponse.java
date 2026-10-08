package VietFreshHub.Order.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record OrderDetailsResponse(Long orderId,String orderCode,String statusLabel,String paymentStatusLabel,
                                   String recipientName,String phone,String addressText,BigDecimal subtotal,
                                   BigDecimal discountTotal,BigDecimal shippingFee,BigDecimal grandTotal,
                                   LocalDateTime placedAt,List<ShopGroup> shops) {
    public record ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                            BigDecimal totalAmount,List<Item> items) {}
    public record Item(String productName,String variantName,String sku,String imageUrl,int quantity,
                       BigDecimal unitPrice,BigDecimal totalAmount) {}
}
