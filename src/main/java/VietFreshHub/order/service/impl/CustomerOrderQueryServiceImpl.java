package VietFreshHub.order.service.impl;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.OrderLabels;

import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.repository.CustomerOrderReadRepository;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderQueryServiceImpl implements CustomerOrderQueryService {
    private final OrderRepository orders;
    private final OrderAddressRepository addresses;
    private final CustomerOrderReadRepository reads;

    @Override
    public Long getCartBadgeTotal(Long customerId) {
        return reads.countActiveCartUnits(customerId);
    }

    @Override
    public OrderDetailsResponse getDetails(Long customerId, Long orderId) {
        if (customerId == null || orderId == null || orderId < 1) {
            throw notFound();
        }
        var order = orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElseThrow(this::notFound);
        var address = addresses.findByOrderOrderId(orderId).orElseThrow(this::notFound);

        // Fixed query count; no lazy catalog traversal or per-shop/per-item database reads.
        Map<Long, List<OrderDetailsResponse.Item>> items = groupItems(customerId, orderId);
        var groups = reads.findShops(customerId, orderId).stream()
                .map(shop -> new OrderDetailsResponse.ShopGroup(
                        shop.getShopOrderId(), shop.getShopName(), OrderLabels.shop(shop.getStatus()),
                        shop.getShippingFee(), shop.getTotalAmount(),
                        List.copyOf(items.getOrDefault(shop.getShopOrderId(), List.of())),
                        shop.getSubtotal(), shop.getDiscountTotal()))
                .toList();
        var payments = reads.findPayments(customerId, orderId).stream()
                .map(payment -> new OrderDetailsResponse.PaymentInfo(
                        payment.getPaymentId(), payment.getMethodName(), payment.getMethodCode(),
                        payment.getStatus().name(), OrderLabels.paymentTransaction(payment.getStatus()),
                        payment.getAmount(), OrderLabels.local(payment.getPaidAt())))
                .toList();

        return new OrderDetailsResponse(
                orderId, order.getOrderCode(), OrderLabels.order(order.getStatus()),
                OrderLabels.payment(order.getPaymentStatus()), address.getRecipientName(), address.getPhone(),
                addressText(address), order.getSubtotal(), order.getDiscountTotal(), order.getShippingFee(),
                order.getGrandTotal(), OrderLabels.local(order.getPlacedAt()), groups, payments);
    }

    private Map<Long, List<OrderDetailsResponse.Item>> groupItems(Long customerId, Long orderId) {
        Map<Long, List<OrderDetailsResponse.Item>> items = new LinkedHashMap<>();
        for (var row : reads.findItems(customerId, orderId)) {
            String image = row.getImageUrl() == null || row.getImageUrl().isBlank() ? null : row.getImageUrl();
            var item = new OrderDetailsResponse.Item(
                    row.getProductName(), row.getVariantName(), row.getSku(), image,
                    row.getQuantity(), row.getUnitPrice(), row.getTotalAmount(), row.getDiscountAmount());
            items.computeIfAbsent(row.getShopOrderId(), key -> new ArrayList<>()).add(item);
        }
        return items;
    }

    private String addressText(OrderAddress address) {
        if (address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        return Stream.of(address.getAddressLine(), address.getWard(), address.getDistrict(), address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }
}
