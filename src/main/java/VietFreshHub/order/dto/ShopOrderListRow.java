package VietFreshHub.order.dto;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.order.service.OrderLabels;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record ShopOrderListRow(Long shopOrderId,Long orderId,String shopName,String status,
                           String paymentStatus,BigDecimal totalAmount,LocalDateTime placedAt,String paymentStatusLabel, List<Product> products, int productCount) {
    public record Product(String productName, String variantName, int quantity, String imageUrl) {}

    public ShopOrderListRow(Long shopOrderId, Long orderId, String shopName, String status, String paymentStatus, BigDecimal totalAmount, LocalDateTime placedAt, String paymentStatusLabel) {
        this(shopOrderId, orderId, shopName, status, paymentStatus, totalAmount, placedAt, paymentStatusLabel, List.of(), 0);
    }
    public ShopOrderListRow(Long shopOrderId,Long orderId,String shopName,String status,
                        String paymentStatus,BigDecimal totalAmount,LocalDateTime placedAt) {
        this(shopOrderId,orderId,shopName,status,paymentStatus,totalAmount,placedAt,OrderLabels.payment(paymentStatus));
    }
    public String orderCode() { return BusinessCodes.format("VFH",shopOrderId); }
    public String statusLabel() { return OrderLabels.customerStatus(status); }
    public LocalDateTime placedAtLocal() { return OrderLabels.local(placedAt); }
}
