package VietFreshHub.Order.service.impl;

import VietFreshHub.Order.dto.IncomingOrderResponse;
import VietFreshHub.Order.dto.OrderCountsResponse;
import VietFreshHub.Order.dto.OrderDetailResponse;
import VietFreshHub.Order.dto.OrderFilterRequest;
import VietFreshHub.Order.dto.OrderItemResponse;
import VietFreshHub.Order.dto.ProcessedOrderResponse;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.entity.OrderAddress;
import VietFreshHub.Order.entity.OrderItem;
import VietFreshHub.Order.entity.ShopOrder;
import VietFreshHub.Order.repository.OrderAddressRepository;
import VietFreshHub.Order.repository.OrderItemRepository;
import VietFreshHub.Order.repository.ShopOrderRepository;
import VietFreshHub.Order.service.OrderService;
import VietFreshHub.Shop.service.ShopService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ShopOrderRepository shopOrderRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopService shopService;

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrderResponse> getIncomingOrders(Authentication authentication, OrderFilterRequest filter) {
        return toOrderResponses(findFilteredShopOrders(authentication, filter, "incoming"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrderResponse> getAllOrders(Authentication authentication, OrderFilterRequest filter) {
        return toOrderResponses(findFilteredShopOrders(authentication, filter, "all"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrderResponse> getConfirmedOrders(Authentication authentication, OrderFilterRequest filter) {
        return toOrderResponses(findFilteredShopOrders(authentication, filter, "confirmed"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrderResponse> getPreparingOrders(Authentication authentication, OrderFilterRequest filter) {
        return toOrderResponses(findFilteredShopOrders(authentication, filter, "preparing"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrderResponse> getReadyOrders(Authentication authentication, OrderFilterRequest filter) {
        return toOrderResponses(findFilteredShopOrders(authentication, filter, "ready"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter) {
        List<ShopOrder> shopOrders = findFilteredShopOrders(authentication, filter, "processed");
        List<ProcessedOrderResponse> orders = new ArrayList<>();

        for (ShopOrder shopOrder : shopOrders) {
            Order order = shopOrder.getOrder();
            orders.add(new ProcessedOrderResponse(
                    shopOrder.getShopOrderId(),
                    order.getOrderCode(),
                    order.getCustomer().getFullName(),
                    order.getPlacedAt(),
                    shopOrder.getTotalAmount(),
                    getOrderStatusLabel(shopOrder.getStatus()),
                    order.getPaymentStatus()
            ));
        }

        return orders;
    }

    @Override
    @Transactional(readOnly = true)
    public long countFilteredOrders(Authentication authentication, OrderFilterRequest filter, String view) {
        return shopOrderRepository.count(buildOrderSpecification(authentication, filter, view));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderCountsResponse getOrderCounts(Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        return new OrderCountsResponse(
                shopOrderRepository.countByShopId(shopId),
                shopOrderRepository.countByShopIdAndStatus(shopId, "PENDING"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "CONFIRMED"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "PREPARING"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "READY_FOR_DELIVERY"),
                shopOrderRepository.countByShopIdAndStatusIn(shopId, List.of("COMPLETED", "CANCELLED"))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long shopOrderId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        ShopOrder shopOrder = shopOrderRepository.findByShopOrderIdAndShopId(shopOrderId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Order order = shopOrder.getOrder();
        OrderAddress address = orderAddressRepository.findByOrderId(order.getOrderId()).orElse(null);
        List<OrderItemResponse> items = new ArrayList<>();

        for (OrderItem item : orderItemRepository.findByShopOrderId(shopOrder.getShopOrderId())) {
            items.add(new OrderItemResponse(
                    item.getProductNameSnapshot(),
                    item.getVariantNameSnapshot(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getTotalAmount()
            ));
        }

        return new OrderDetailResponse(
                order.getOrderCode(),
                order.getPlacedAt(),
                order.getCustomer().getFullName(),
                address == null ? null : address.getRecipientName(),
                address == null ? null : address.getPhone(),
                address == null ? null : formatDeliveryAddress(address),
                shopOrder.getStatus(),
                getOrderStatusLabel(shopOrder.getStatus()),
                getPaymentStatusLabel(order.getPaymentStatus()),
                shopOrder.getTotalAmount(),
                items
        );
    }

    @Override
    @Transactional
    public void confirmOrder(Long shopOrderId, Authentication authentication) {
        ShopOrder shopOrder = getShopOrderForUpdate(shopOrderId, authentication);
        if (!"PENDING".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể xác nhận đơn hàng đang chờ xác nhận.");
        }
        shopOrder.confirm();
    }

    @Override
    @Transactional
    public void startPreparing(Long shopOrderId, Authentication authentication) {
        ShopOrder shopOrder = getShopOrderForUpdate(shopOrderId, authentication);
        if (!"CONFIRMED".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể bắt đầu chuẩn bị đơn hàng đã xác nhận.");
        }
        shopOrder.startPreparing();
    }

    @Override
    @Transactional
    public void markReady(Long shopOrderId, Authentication authentication) {
        ShopOrder shopOrder = getShopOrderForUpdate(shopOrderId, authentication);
        if (!"PREPARING".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể đánh dấu sẵn sàng giao hàng cho đơn hàng đang chuẩn bị.");
        }
        shopOrder.markReady();
    }

    private List<ShopOrder> findFilteredShopOrders(Authentication authentication, OrderFilterRequest filter,
                                                 String view) {
        Specification<ShopOrder> specification = buildOrderSpecification(authentication, filter, view);
        return shopOrderRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "order.placedAt"));
    }

    private Specification<ShopOrder> buildOrderSpecification(Authentication authentication, OrderFilterRequest filter,
                                                            String view) {
        Long shopId = shopService.getManagedShopId(authentication);
        validateDateRange(filter);
        List<String> statuses = switch (view) {
            case "all" -> List.of();
            case "incoming" -> List.of("PENDING");
            case "confirmed" -> List.of("CONFIRMED");
            case "preparing" -> List.of("PREPARING");
            case "ready" -> List.of("READY_FOR_DELIVERY");
            case "processed" -> List.of("COMPLETED", "CANCELLED");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách đơn hàng không hợp lệ.");
        };
        boolean filterStatus = "all".equals(view) || "processed".equals(view);

        return (root, query, builder) -> {
            Join<ShopOrder, Order> order = root.join("order", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("shopId"), shopId));

            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (filterStatus && filter.getStatus() != null && !filter.getStatus().isBlank()) {
                predicates.add(builder.equal(root.get("status"), filter.getStatus().trim()));
            }
            if (filter.getFromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(order.get("placedAt"), filter.getFromDate().atStartOfDay()));
            }
            if (filter.getToDate() != null) {
                predicates.add(builder.lessThan(order.get("placedAt"), filter.getToDate().plusDays(1).atStartOfDay()));
            }
            if (filter.getPriceRange() != null && !filter.getPriceRange().isBlank()) {
                Path<BigDecimal> amount = root.get("totalAmount");
                switch (filter.getPriceRange().trim()) {
                    case "UNDER_100K" -> predicates.add(builder.lessThan(amount, BigDecimal.valueOf(100000)));
                    case "FROM_100K_TO_300K" -> {
                        predicates.add(builder.greaterThanOrEqualTo(amount, BigDecimal.valueOf(100000)));
                        predicates.add(builder.lessThan(amount, BigDecimal.valueOf(300000)));
                    }
                    case "FROM_300K_TO_500K" -> {
                        predicates.add(builder.greaterThanOrEqualTo(amount, BigDecimal.valueOf(300000)));
                        predicates.add(builder.lessThan(amount, BigDecimal.valueOf(500000)));
                    }
                    case "FROM_500K_TO_1M" -> {
                        predicates.add(builder.greaterThanOrEqualTo(amount, BigDecimal.valueOf(500000)));
                        predicates.add(builder.lessThan(amount, BigDecimal.valueOf(1000000)));
                    }
                    case "FROM_1M_TO_2M" -> {
                        predicates.add(builder.greaterThanOrEqualTo(amount, BigDecimal.valueOf(1000000)));
                        predicates.add(builder.lessThanOrEqualTo(amount, BigDecimal.valueOf(2000000)));
                    }
                    case "OVER_2M" -> predicates.add(builder.greaterThan(amount, BigDecimal.valueOf(2000000)));
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng giá không hợp lệ.");
                }
            }
            if (filter.getPaymentStatus() != null && !filter.getPaymentStatus().isBlank()) {
                String paymentStatus = filter.getPaymentStatus().trim();
                if (!List.of("UNPAID", "PAID").contains(paymentStatus)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái thanh toán không hợp lệ.");
                }
                predicates.add(builder.equal(order.get("paymentStatus"), paymentStatus));
            }
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String keyword = filter.getKeyword().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
                String pattern = "%" + keyword + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(order.get("orderCode")), pattern, '\\'),
                        builder.like(builder.lower(order.join("customer", JoinType.LEFT).get("fullName")), pattern, '\\')
                ));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateDateRange(OrderFilterRequest filter) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = filter.getFromDate();
        LocalDate toDate = filter.getToDate();

        if (fromDate != null && fromDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Từ ngày không được lớn hơn ngày hiện tại.");
        }
        if (toDate != null && toDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đến ngày không được lớn hơn ngày hiện tại.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Từ ngày không được sau Đến ngày.");
        }
    }

    private List<IncomingOrderResponse> toOrderResponses(List<ShopOrder> shopOrders) {
        List<IncomingOrderResponse> orders = new ArrayList<>();

        for (ShopOrder shopOrder : shopOrders) {
            Order order = shopOrder.getOrder();
            orders.add(new IncomingOrderResponse(
                    shopOrder.getShopOrderId(),
                    order.getOrderCode(),
                    order.getCustomer().getFullName(),
                    order.getPlacedAt(),
                    shopOrder.getTotalAmount(),
                    shopOrder.getStatus(),
                    getOrderStatusLabel(shopOrder.getStatus()),
                    order.getPaymentStatus()
            ));
        }

        return orders;
    }

    private ShopOrder getShopOrderForUpdate(Long shopOrderId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        return shopOrderRepository.findForUpdateByShopOrderIdAndShopId(shopOrderId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String formatDeliveryAddress(OrderAddress address) {
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{address.getAddressLine(), address.getWard(),
                address.getDistrict(), address.getProvince()}) {
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return String.join(", ", parts);
    }

    private String getOrderStatusLabel(String status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "PREPARING" -> "Đang chuẩn bị";
            case "READY_FOR_DELIVERY" -> "Sẵn sàng giao hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> "Không xác định";
        };
    }

    private String getPaymentStatusLabel(String status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case "UNPAID" -> "Chưa thanh toán";
            case "PAID" -> "Đã thanh toán";
            default -> status;
        };
    }
}
