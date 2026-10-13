package VietFreshHub.Order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailsResponse(Long orderId,String orderCode,String statusLabel,String paymentStatusLabel,
                                   String recipientName,String phone,String addressText,BigDecimal subtotal,
                                   BigDecimal discountTotal,BigDecimal shippingFee,BigDecimal grandTotal,
                                   LocalDateTime placedAt,List<ShopGroup> shops,List<PaymentInfo> payments) {
    public OrderDetailsResponse {
        shops=List.copyOf(shops); payments=List.copyOf(payments);
    }
    // Preserve checkout/confirmation consumers of the original TV3 DTO contract.
    public OrderDetailsResponse(Long orderId,String orderCode,String statusLabel,String paymentStatusLabel,
                                String recipientName,String phone,String addressText,BigDecimal subtotal,
                                BigDecimal discountTotal,BigDecimal shippingFee,BigDecimal grandTotal,
                                LocalDateTime placedAt,List<ShopGroup> shops) {
        this(orderId,orderCode,statusLabel,paymentStatusLabel,recipientName,phone,addressText,subtotal,
                discountTotal,shippingFee,grandTotal,placedAt,shops,List.of());
    }
    public record ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                            BigDecimal totalAmount,List<Item> items,BigDecimal subtotal,BigDecimal discountTotal) {
        public ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                         BigDecimal totalAmount,List<Item> items) {
            this(shopOrderId,shopName,statusLabel,shippingFee,totalAmount,items,
                    items.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add),BigDecimal.ZERO);
        }
    }
    public record Item(String productName,String variantName,String sku,String imageUrl,int quantity,
                       BigDecimal unitPrice,BigDecimal totalAmount,BigDecimal discountAmount) {
        public Item(String productName,String variantName,String sku,String imageUrl,int quantity,
                    BigDecimal unitPrice,BigDecimal totalAmount) {
            this(productName,variantName,sku,imageUrl,quantity,unitPrice,totalAmount,BigDecimal.ZERO);
        }
    }
    public record PaymentInfo(Long paymentId,String methodName,String methodCode,String status,String statusLabel,
                              BigDecimal amount,LocalDateTime paidAt) {}
}
