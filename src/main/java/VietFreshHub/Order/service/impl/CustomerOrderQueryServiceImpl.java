package VietFreshHub.Order.service.impl;

import VietFreshHub.Order.dto.OrderDetailsResponse;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.entity.OrderItem;
import VietFreshHub.Order.repository.*;
import VietFreshHub.Order.service.CustomerOrderQueryService;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Comparator;

@Service @RequiredArgsConstructor
public class CustomerOrderQueryServiceImpl implements CustomerOrderQueryService {
    private final OrderRepository orders;
    private final OrderAddressRepository addresses;
    private final ShopOrderRepository shopOrders;
    @Override @Transactional(readOnly=true)
    public OrderDetailsResponse getDetails(Long customerId,Long orderId) {
        Order order=orders.findByOrderIdAndCustomerUserId(orderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var address=addresses.findByOrderOrderId(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của đơn hàng."));
        var groups=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId).stream().map(shop -> {
            var items=shop.getItems().stream().sorted(Comparator.comparing(OrderItem::getOrderItemId))
                    .map(item -> new OrderDetailsResponse.Item(item.getProductNameSnapshot(),item.getVariantNameSnapshot(),item.getSkuSnapshot(),
                            item.getVariant().getProduct().getImageUrl(),item.getQuantity(),item.getUnitPrice(),item.getTotalAmount())).toList();
            return new OrderDetailsResponse.ShopGroup(shop.getShopOrderId(),shop.getShop().getShopName(),
                    switch(shop.getStatus()) {
                        case PENDING -> "Chờ cửa hàng xác nhận";
                        case CONFIRMED -> "Đã xác nhận";
                        case PREPARING -> "Đang chuẩn bị";
                        case READY_FOR_DELIVERY -> "Sẵn sàng giao";
                        case OUT_FOR_DELIVERY -> "Đang giao";
                        case COMPLETED -> "Hoàn thành";
                        case CANCELLED -> "Đã hủy";
                    },shop.getShippingFee(),shop.getTotalAmount(),items);
        }).toList();
        return new OrderDetailsResponse(orderId,order.getOrderCode(),switch(order.getStatus()) {
            case PENDING -> "Chờ cửa hàng xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case PROCESSING -> "Đang xử lý";
            case PARTIALLY_COMPLETED -> "Hoàn thành một phần";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
        },switch(order.getPaymentStatus()) {
            case UNPAID -> "Chưa thu tiền";
            case PENDING -> "Đang chờ thanh toán";
            case PAID -> "Đã thanh toán";
            case PARTIALLY_REFUNDED -> "Hoàn tiền một phần";
            case REFUNDED -> "Đã hoàn tiền";
            case FAILED -> "Thanh toán thất bại";
        },address.getRecipientName(),address.getPhone(),address.getAddressLine()+", "+address.getWard()+", "+address.getDistrict()+", "+address.getProvince(),
                order.getSubtotal(),order.getDiscountTotal(),order.getShippingFee(),order.getGrandTotal(),
                LocalDateTime.ofInstant(order.getPlacedAt().toInstant(ZoneOffset.UTC),ZoneId.of("Asia/Ho_Chi_Minh")),groups);
    }
}
