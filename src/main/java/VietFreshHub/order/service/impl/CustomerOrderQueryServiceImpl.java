package VietFreshHub.order.service.impl;

import VietFreshHub.order.dto.CustomerOrderStage;
import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.repository.CustomerOrderReadRepository;
import VietFreshHub.order.repository.CustomerOrderReadRepository.DeliverySummary;
import VietFreshHub.order.repository.CustomerOrderReadRepository.ItemSnapshot;
import VietFreshHub.order.repository.CustomerOrderReadRepository.PaymentSummary;
import VietFreshHub.order.repository.CustomerOrderReadRepository.ShopSummary;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        Order order = orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElseThrow(this::notFound);
        OrderAddress address = addresses.findByOrderOrderId(orderId).orElseThrow(this::notFound);

        // Giữ số query cố định: lấy theo nhóm, không đọc DB riêng cho từng sản phẩm.
        Map<Long, List<OrderDetailsResponse.Item>> itemsByShop = groupItems(customerId, orderId);
        Map<Long, DeliverySummary> deliveriesByShop = reads.findDeliveries(customerId, orderId).stream()
                .collect(Collectors.toMap(DeliverySummary::getShopOrderId, row -> row));
        List<OrderDetailsResponse.ShopGroup> shopGroups = readShopGroups(customerId, orderId, itemsByShop, deliveriesByShop);
        List<OrderDetailsResponse.PaymentInfo> paymentInfos = readPayments(customerId, orderId);
        String stageCode = orders.findCustomerStage(orderId, customerId).orElseThrow(this::notFound);
        String stageLabel = CustomerOrderStage.valueOf(stageCode).label();

        return new OrderDetailsResponse(
                orderId, order.getOrderCode(), stageLabel,
                OrderLabels.payment(order.getPaymentStatus()), address.getRecipientName(), address.getPhone(),
                addressText(address), order.getSubtotal(), order.getDiscountTotal(), order.getShippingFee(),
                order.getGrandTotal(), OrderLabels.local(order.getPlacedAt()), shopGroups, paymentInfos);
    }

    private List<OrderDetailsResponse.ShopGroup> readShopGroups(Long customerId, Long orderId,
            Map<Long, List<OrderDetailsResponse.Item>> itemsByShop, Map<Long, DeliverySummary> deliveriesByShop) {
        List<OrderDetailsResponse.ShopGroup> groups = new ArrayList<>();
        for (ShopSummary shop : reads.findShops(customerId, orderId)) {
            List<OrderDetailsResponse.Item> shopItems = List.copyOf(itemsByShop.getOrDefault(shop.getShopOrderId(), List.of()));
            DeliverySummary delivery = deliveriesByShop.get(shop.getShopOrderId());
            OrderDetailsResponse.ShopGroup group = new OrderDetailsResponse.ShopGroup(
                    shop.getShopOrderId(), shop.getShopName(), OrderLabels.shop(shop.getStatus()),
                    shop.getShippingFee(), shop.getTotalAmount(), shopItems,
                    shop.getSubtotal(), shop.getDiscountTotal(), shop.getStatus(), deliveryInfo(delivery));
            groups.add(group);
        }
        return List.copyOf(groups);
    }

    private List<OrderDetailsResponse.PaymentInfo> readPayments(Long customerId, Long orderId) {
        List<OrderDetailsResponse.PaymentInfo> result = new ArrayList<>();
        for (PaymentSummary payment : reads.findPayments(customerId, orderId)) {
            OrderDetailsResponse.PaymentInfo info = new OrderDetailsResponse.PaymentInfo(
                    payment.getPaymentId(), payment.getMethodName(), payment.getMethodCode(),
                    payment.getStatus().name(), OrderLabels.paymentTransaction(payment.getStatus(), payment.getMethodCode()),
                    payment.getAmount(), OrderLabels.local(payment.getPaidAt()),
                    OrderLabels.local(payment.getCreatedAt()), payment.getTransactionReference());
            result.add(info);
        }
        return List.copyOf(result);
    }

    private OrderDetailsResponse.DeliveryInfo deliveryInfo(DeliverySummary row) {
        if (row == null) {
            return null;
        }
        String label = switch (row.getStatus()) {
            case "FAILED" -> "Giao hàng thất bại";
            case "REDELIVERY_SCHEDULED" -> "Chờ giao lại";
            case "DELIVERED" -> "Đã giao hàng";
            default -> OrderLabels.delivery(row.getStatus());
        };
        String reason = null;
        if ("FAILED".equals(row.getStatus()) && row.getFailureReason() != null && !row.getFailureReason().isBlank()) {
            reason = row.getFailureReason().strip();
        }
        return new OrderDetailsResponse.DeliveryInfo(row.getStatus(), label, OrderLabels.local(row.getAssignedAt()),
                OrderLabels.local(row.getPickedUpAt()), OrderLabels.local(row.getDeliveredAt()),
                OrderLabels.local(row.getRedeliveryScheduledAt()), reason);
    }

    private Map<Long, List<OrderDetailsResponse.Item>> groupItems(Long customerId, Long orderId) {
        Map<Long, List<OrderDetailsResponse.Item>> itemsByShop = new LinkedHashMap<>();
        for (ItemSnapshot row : reads.findItems(customerId, orderId)) {
            String image = row.getImageUrl();
            if (image == null || image.isBlank()) {
                image = null;
            }
            OrderDetailsResponse.Item item = new OrderDetailsResponse.Item(
                    row.getProductName(), row.getVariantName(), row.getSku(), image,
                    row.getQuantity(), row.getUnitPrice(), row.getTotalAmount(), row.getDiscountAmount());
            List<OrderDetailsResponse.Item> shopItems = itemsByShop.get(row.getShopOrderId());
            if (shopItems == null) {
                shopItems = new ArrayList<>();
                itemsByShop.put(row.getShopOrderId(), shopItems);
            }
            shopItems.add(item);
        }
        return itemsByShop;
    }

    private String addressText(OrderAddress address) {
        if (address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        List<String> parts = new ArrayList<>();
        addAddressPart(parts, address.getAddressLine());
        addAddressPart(parts, address.getWard());
        addAddressPart(parts, address.getDistrict());
        addAddressPart(parts, address.getProvince());
        return String.join(", ", parts);
    }

    private void addAddressPart(List<String> parts, String part) {
        if (part != null && !part.isBlank()) {
            parts.add(part);
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }
}
