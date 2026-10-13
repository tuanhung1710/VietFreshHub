package VietFreshHub.Order.service.impl;

import VietFreshHub.Order.dto.OrderDetailsResponse;
import VietFreshHub.Order.repository.*;
import VietFreshHub.Order.service.CustomerOrderQueryService;
import VietFreshHub.Order.util.OrderLabels;
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
                shop.getShopOrderId(),shop.getShopName(),OrderLabels.shop(shop.getStatus().name()),shop.getShippingFee(),shop.getTotalAmount(),
                List.copyOf(items.getOrDefault(shop.getShopOrderId(),List.of())),shop.getSubtotal(),shop.getDiscountTotal())).toList();
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
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."); }
}
