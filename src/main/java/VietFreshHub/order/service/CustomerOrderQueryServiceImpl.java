package VietFreshHub.order.service;

import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CustomerOrderQueryServiceImpl implements CustomerOrderQueryService {
    private final OrderRepository orders;
    private final OrderAddressRepository addresses;
    private final CustomerOrderReadRepository reads;
    private final ShopOrderRepository shopOrders;

    @Override @Transactional(readOnly=true)
    public Long getCartBadgeTotal(Long customerId) {
        return reads.countActiveCartUnits(customerId);
    }

    @Override @Transactional(readOnly=true)
    public OrderDetailsResponse getDetails(Long customerId,Long orderId) {
        if(customerId==null || orderId==null || orderId<1) throw notFound();
        var order=orders.findByOrderIdAndCustomerUserId(orderId,customerId).orElseThrow(this::notFound);
        var address=addresses.findByOrderOrderId(orderId).orElseThrow(this::notFound);
        // Fixed query count; no lazy catalog traversal or per-shop/per-item database reads.
        Map<Long,List<OrderDetailsResponse.Item>> items=new LinkedHashMap<>();
        for(var row:reads.findItems(customerId,orderId)) {
            String image=row.getImageUrl()==null || row.getImageUrl().isBlank() ? null : row.getImageUrl();
            items.computeIfAbsent(row.getShopOrderId(),key -> new ArrayList<>()).add(new OrderDetailsResponse.Item(
                    row.getProductName(),row.getVariantName(),row.getSku(),image,row.getQuantity(),row.getUnitPrice(),row.getTotalAmount(),row.getDiscountAmount()));
        }
        var groups=reads.findShops(customerId,orderId).stream().map(shop -> new OrderDetailsResponse.ShopGroup(
                shop.getShopOrderId(),shop.getShopName(),OrderLabels.shop(shop.getStatus()),shop.getShippingFee(),shop.getTotalAmount(),
                List.copyOf(items.getOrDefault(shop.getShopOrderId(),List.of())),shop.getSubtotal(),shop.getDiscountTotal(),shop.getPaymentStatus())).toList();
        var payments=reads.findPayments(customerId,orderId).stream().map(payment -> new OrderDetailsResponse.PaymentInfo(
                payment.getPaymentId(),payment.getMethodName(),payment.getMethodCode(),payment.getStatus().name(),
                OrderLabels.paymentTransaction(payment.getStatus()),payment.getAmount(),OrderLabels.local(payment.getPaidAt()))).toList();
        String addressText=address.getFormattedAddress()!=null && !address.getFormattedAddress().isBlank() ? address.getFormattedAddress()
                : java.util.stream.Stream.of(address.getAddressLine(),address.getWard(),address.getDistrict(),address.getProvince())
                    .filter(part -> part!=null && !part.isBlank()).collect(Collectors.joining(", "));
        return new OrderDetailsResponse(orderId,order.getOrderCode(),OrderLabels.order(order.getStatus()),OrderLabels.payment(order.getPaymentStatus()),
                address.getRecipientName(),address.getPhone(),addressText,order.getSubtotal(),order.getDiscountTotal(),order.getShippingFee(),
                order.getGrandTotal(),OrderLabels.local(order.getPlacedAt()),groups,payments);
    }
    @Override @Transactional(readOnly=true)
    public OrderDetailsResponse getShopDetails(Long customerId,Long shopOrderId) {
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId).orElseThrow(this::notFound);
        var order=shop.getOrder();
        var address=addresses.findByOrderOrderId(order.getOrderId()).orElseThrow(this::notFound);
        var status=shopOrders.findCustomerStatuses(customerId,List.of(shopOrderId)).stream().findFirst().orElseThrow(this::notFound);
        var items=reads.findShopItems(customerId,shopOrderId).stream().map(row -> {
            String image=row.getImageUrl()==null || row.getImageUrl().isBlank() ? null : row.getImageUrl();
            return new OrderDetailsResponse.Item(row.getProductName(),row.getVariantName(),row.getSku(),image,
                    row.getQuantity(),row.getUnitPrice(),row.getTotalAmount(),row.getDiscountAmount());
        }).toList();
        var group=new OrderDetailsResponse.ShopGroup(shop.getShopOrderId(),shop.getShop().getShopName(),status.statusLabel(),
                shop.getShippingFee(),shop.getTotalAmount(),items,shop.getSubtotal(),shop.getDiscountTotal(),shop.getPaymentStatus());
        String addressText=address.getFormattedAddress()!=null && !address.getFormattedAddress().isBlank() ? address.getFormattedAddress()
                : java.util.stream.Stream.of(address.getAddressLine(),address.getWard(),address.getDistrict(),address.getProvince())
                    .filter(part -> part!=null && !part.isBlank()).collect(Collectors.joining(", "));
        return new OrderDetailsResponse(order.getOrderId(),shop.getShopOrderCode(),status.statusLabel(),
                OrderLabels.payment(shop.getPaymentStatus()),address.getRecipientName(),address.getPhone(),addressText,
                shop.getSubtotal(),shop.getDiscountTotal(),shop.getShippingFee(),shop.getTotalAmount(),
                OrderLabels.local(order.getPlacedAt()),List.of(group),List.of());
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."); }
}
