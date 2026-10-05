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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ShopOrderRepository shopOrderRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopService shopService;

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getIncomingOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "incoming", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getAllOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "all", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getConfirmedOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "confirmed", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getPreparingOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "preparing", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getReadyOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "ready", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "processed", page).map(shopOrder -> {
            Order order = shopOrder.getOrder();
            return new ProcessedOrderResponse(
                    shopOrder.getShopOrderId(),
                    order.getOrderCode(),
                    order.getCustomer().getFullName(),
                    order.getPlacedAt(),
                    shopOrder.getTotalAmount(),
                    getOrderStatusLabel(shopOrder.getStatus()),
                    order.getPaymentStatus()
            );
        });
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
        return toOrderDetailResponse(shopOrder);
    }

    private OrderDetailResponse toOrderDetailResponse(ShopOrder shopOrder) {
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

    @Override
    @Transactional
    public void confirmOrders(List<Long> shopOrderIds, Authentication authentication) {
        List<ShopOrder> shopOrders = getShopOrdersForUpdate(shopOrderIds, authentication);
        for (ShopOrder shopOrder : shopOrders) {
            if (!"PENDING".equals(shopOrder.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ có thể xác nhận đơn hàng đang chờ xác nhận.");
            }
        }
        for (ShopOrder shopOrder : shopOrders) {
            shopOrder.confirm();
        }
    }

    @Override
    @Transactional
    public void startPreparingOrders(List<Long> shopOrderIds, Authentication authentication) {
        List<ShopOrder> shopOrders = getShopOrdersForUpdate(shopOrderIds, authentication);
        for (ShopOrder shopOrder : shopOrders) {
            if (!"CONFIRMED".equals(shopOrder.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ có thể bắt đầu chuẩn bị đơn hàng đã xác nhận.");
            }
        }
        for (ShopOrder shopOrder : shopOrders) {
            shopOrder.startPreparing();
        }
    }

    @Override
    @Transactional
    public void markOrdersReady(List<Long> shopOrderIds, Authentication authentication) {
        List<ShopOrder> shopOrders = getShopOrdersForUpdate(shopOrderIds, authentication);
        for (ShopOrder shopOrder : shopOrders) {
            if (!"PREPARING".equals(shopOrder.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ có thể đánh dấu sẵn sàng giao hàng cho đơn hàng đang chuẩn bị.");
            }
        }
        for (ShopOrder shopOrder : shopOrders) {
            shopOrder.markReady();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getBulkInvoices(List<Long> shopOrderIds, Authentication authentication, String view) {
        Long shopId = shopService.getManagedShopId(authentication);
        String expectedStatus = switch (view) {
            case "preparing" -> "PREPARING";
            case "ready" -> "READY_FOR_DELIVERY";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách in hóa đơn không hợp lệ.");
        };
        List<ShopOrder> shopOrders = new ArrayList<>();
        for (Long shopOrderId : validateSelectedIds(shopOrderIds)) {
            ShopOrder shopOrder = shopOrderRepository.findByShopOrderIdAndShopId(shopOrderId, shopId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!expectedStatus.equals(shopOrder.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Đơn hàng được chọn không còn thuộc trạng thái của danh sách in hóa đơn.");
            }
            shopOrders.add(shopOrder);
        }
        List<OrderDetailResponse> invoices = new ArrayList<>();
        for (ShopOrder shopOrder : shopOrders) {
            invoices.add(toOrderDetailResponse(shopOrder));
        }
        return invoices;
    }

    private List<Long> validateSelectedIds(List<Long> shopOrderIds) {
        if (shopOrderIds == null || shopOrderIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một đơn hàng.");
        }
        TreeSet<Long> selectedIds = new TreeSet<>();
        for (Long shopOrderId : shopOrderIds) {
            if (shopOrderId == null || shopOrderId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng được chọn không hợp lệ.");
            }
            selectedIds.add(shopOrderId);
        }
        return new ArrayList<>(selectedIds);
    }

    private List<ShopOrder> getShopOrdersForUpdate(List<Long> shopOrderIds, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        List<ShopOrder> shopOrders = new ArrayList<>();
        for (Long shopOrderId : validateSelectedIds(shopOrderIds)) {
            shopOrders.add(shopOrderRepository.findForUpdateByShopOrderIdAndShopId(shopOrderId, shopId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        }
        return shopOrders;
    }

    private Page<ShopOrder> findFilteredShopOrders(Authentication authentication, OrderFilterRequest filter,
                                                 String view, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        }
        Specification<ShopOrder> specification = buildOrderSpecification(authentication, filter, view);
        int pageSize = "all".equals(view) || "processed".equals(view) ? 50 : 15;
        Pageable pageable = PageRequest.of(page, pageSize,
                Sort.by(Sort.Direction.DESC, "order.placedAt", "shopOrderId"));
        return shopOrderRepository.findAll(specification, pageable);
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

    private IncomingOrderResponse toOrderResponse(ShopOrder shopOrder) {
        Order order = shopOrder.getOrder();
        return new IncomingOrderResponse(
                shopOrder.getShopOrderId(),
                order.getOrderCode(),
                order.getCustomer().getFullName(),
                order.getPlacedAt(),
                shopOrder.getTotalAmount(),
                shopOrder.getStatus(),
                getOrderStatusLabel(shopOrder.getStatus()),
                order.getPaymentStatus()
        );
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
