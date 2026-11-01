package VietFreshHub.order.dto;

import VietFreshHub.common.BusinessCodes;
import VietFreshHub.order.service.OrderLabels;
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
    public boolean cod() { return !payments.isEmpty() && payments.stream().allMatch(p -> "COD".equals(p.methodCode())); }
    public boolean hasItems() { return shops.stream().anyMatch(shop -> !shop.items().isEmpty()); }
    public boolean hasCancelledShops() { return shops.stream().anyMatch(shop -> "CANCELLED".equals(shop.statusCode())); }
    // Preserve checkout/confirmation consumers of the original TV3 DTO contract.
    public OrderDetailsResponse(Long orderId,String orderCode,String statusLabel,String paymentStatusLabel,
                                String recipientName,String phone,String addressText,BigDecimal subtotal,
                                BigDecimal discountTotal,BigDecimal shippingFee,BigDecimal grandTotal,
                                LocalDateTime placedAt,List<ShopGroup> shops) {
        this(orderId,orderCode,statusLabel,paymentStatusLabel,recipientName,phone,addressText,subtotal,
                discountTotal,shippingFee,grandTotal,placedAt,shops,List.of());
    }
    public record ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                            BigDecimal totalAmount,List<Item> items,BigDecimal subtotal,BigDecimal discountTotal,
                            String statusCode,DeliveryInfo delivery,String paymentStatus,String paymentStatusLabel) {
        public ShopGroup { items=List.copyOf(items); }
        public String orderCode() { return BusinessCodes.format("VFH",shopOrderId); }
        public ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                         BigDecimal totalAmount,List<Item> items,BigDecimal subtotal,BigDecimal discountTotal,
                         String statusCode,DeliveryInfo delivery) {
            this(shopOrderId,shopName,statusLabel,shippingFee,totalAmount,items,subtotal,discountTotal,statusCode,delivery,null,null);
        }
        public ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                         BigDecimal totalAmount,List<Item> items,BigDecimal subtotal,BigDecimal discountTotal) {
            this(shopOrderId,shopName,statusLabel,shippingFee,totalAmount,items,subtotal,discountTotal,null,(DeliveryInfo)null);
        }
        public ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                         BigDecimal totalAmount,List<Item> items,BigDecimal subtotal,BigDecimal discountTotal,String paymentStatus) {
            this(shopOrderId,shopName,statusLabel,shippingFee,totalAmount,items,subtotal,discountTotal,null,null,paymentStatus,OrderLabels.payment(paymentStatus));
        }
        public ShopGroup(Long shopOrderId,String shopName,String statusLabel,BigDecimal shippingFee,
                         BigDecimal totalAmount,List<Item> items,String paymentStatus) {
            this(shopOrderId,shopName,statusLabel,shippingFee,totalAmount,items,
                    items.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add),BigDecimal.ZERO,paymentStatus);
        }
    }
    public record Item(String productName,String variantName,String sku,String imageUrl,int quantity,
                       BigDecimal unitPrice,BigDecimal totalAmount,BigDecimal discountAmount) {
        public Item(String productName,String variantName,String sku,String imageUrl,int quantity,
                    BigDecimal unitPrice,BigDecimal totalAmount) {
            this(productName,variantName,sku,imageUrl,quantity,unitPrice,totalAmount,BigDecimal.ZERO);
        }
    }
    public record DeliveryInfo(String status,String statusLabel,LocalDateTime assignedAt,LocalDateTime pickedUpAt,
                               LocalDateTime deliveredAt,LocalDateTime redeliveryScheduledAt,String failureReason) {}
    public record PaymentInfo(Long paymentId,String methodName,String methodCode,String status,String statusLabel,
                              BigDecimal amount,LocalDateTime paidAt,LocalDateTime createdAt,String transactionReference) {
        public PaymentInfo {
            transactionReference=transactionReference==null || transactionReference.isBlank() ? null : transactionReference;
        }
        public PaymentInfo(Long paymentId,String methodName,String methodCode,String status,String statusLabel,
                           BigDecimal amount,LocalDateTime paidAt) {
            this(paymentId,methodName,methodCode,status,statusLabel,amount,paidAt,null,null);
        }
        public boolean pendingCod() { return "COD".equals(methodCode) && "PENDING".equals(status); }
    }
}
