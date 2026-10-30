package VietFreshHub.order.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.service.DeliveryCreationService;
import VietFreshHub.delivery.service.DeliveryReturnQueryService;
import VietFreshHub.order.dto.IncomingOrderResponse;
import VietFreshHub.order.dto.OrderCountsResponse;
import VietFreshHub.order.dto.OrderDetailResponse;
import VietFreshHub.order.dto.OrderFilterRequest;
import VietFreshHub.order.dto.OrderItemResponse;
import VietFreshHub.order.dto.OrderProcessingHistoryEntry;
import VietFreshHub.order.dto.ProcessedOrderResponse;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.OrderDeliveryView;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderDeliveryReadRepository;
import VietFreshHub.order.repository.OrderStatusHistoryRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String[] VIETNAMESE_DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };
    private static final String[] INVOICE_NUMBER_SCALES = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"};

    private final ShopOrderRepository shopOrderRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final OrderDeliveryReadRepository orderDeliveryReadRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ShopService shopService;
    private final PaymentService paymentService;
    private final DeliveryCreationService deliveryCreationService;
    private final DeliveryReturnQueryService deliveryReturnQueryService;
    private final OrderNotificationService orderNotificationService;
    private final AuthService authService;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getIncomingOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "incoming", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getAllOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        Page<ShopOrder> orders = findFilteredShopOrders(authentication, filter, "all", page);
        List<Long> deliveringOrderIds = orders.getContent().stream()
                .filter(shopOrder -> "OUT_FOR_DELIVERY".equals(shopOrder.getStatus()))
                .map(ShopOrder::getShopOrderId)
                .toList();
        Set<Long> failedOrderIds = getFailedShopOrderIds(deliveringOrderIds);
        return orders.map(shopOrder -> toOrderResponse(shopOrder, failedOrderIds.contains(shopOrder.getShopOrderId())));
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
    public Page<IncomingOrderResponse> getFailedOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "failed", page)
                .map(shopOrder -> toOrderResponse(shopOrder, true));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomingOrderResponse> getCancelledOrders(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "cancelled", page).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProcessedOrderResponse> getProcessedOrderHistory(Authentication authentication, OrderFilterRequest filter, int page) {
        return findFilteredShopOrders(authentication, filter, "processed", page).map(shopOrder -> {
            Order order = shopOrder.getOrder();
            return new ProcessedOrderResponse(
                    shopOrder.getShopOrderId(),
                    shopOrder.getShopOrderCode(),
                    order.getCustomer().getFullName(),
                    OrderLabels.local(order.getPlacedAt()),
                    shopOrder.getTotalAmount(),
                    "OUT_FOR_DELIVERY".equals(shopOrder.getStatus())
                            ? "Giao thất bại - đã nhận lại hàng" : getOrderStatusLabel(shopOrder.getStatus()),
                    shopOrder.getPaymentStatus()
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
                shopOrderRepository.count(buildOrderSpecification(authentication, new OrderFilterRequest(), "incoming")),
                shopOrderRepository.countByShopIdAndStatus(shopId, "CONFIRMED"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "PREPARING"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "READY_FOR_DELIVERY"),
                shopOrderRepository.count(buildOrderSpecification(authentication, new OrderFilterRequest(), "processed")),
                shopOrderRepository.count(buildOrderSpecification(authentication, new OrderFilterRequest(), "failed")),
                shopOrderRepository.countByShopIdAndStatus(shopId, "CANCELLED")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long shopOrderId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        ShopOrder shopOrder = shopOrderRepository.findByShopOrderIdAndShopId(shopOrderId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long orderId = shopOrder.getOrder().getOrderId();
        OrderAddress address = orderAddressRepository.findByOrderId(orderId).orElse(null);
        String shopAddress = formatInvoiceShopAddress(shopService.findLocationByShopId(shopId).orElse(null));
        String paymentMethod = paymentService.getInvoicePaymentMethodLabel(orderId);
        return toOrderDetailResponse(shopOrder, address, shopAddress, paymentMethod);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getDeliveryIdForManagedShopOrder(Long shopOrderId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        return orderDeliveryReadRepository.findByShopOrder_ShopOrderIdAndShopOrder_ShopId(shopOrderId, shopId)
                .map(OrderDeliveryView::getDeliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderAddress getOrderAddress(Long orderId) {
        return orderAddressRepository.findByOrderId(orderId).orElse(null);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<OrderProcessingHistoryEntry> getProcessingHistoryForAdmin(Long shopOrderId,
                                                                         Authentication authentication) {
        authService.getCurrentUserId(authentication);
        ShopOrder shopOrder = shopOrderRepository.findById(shopOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        List<OrderProcessingHistoryEntry> history = new ArrayList<>();
        history.add(new OrderProcessingHistoryEntry("Khách hàng đặt đơn",
                OrderLabels.local(shopOrder.getOrder().getPlacedAt()), null));
        for (OrderStatusHistory event : orderStatusHistoryRepository
                .findByShopOrder_ShopOrderIdAndStatusInOrderByCreatedAtAscHistoryIdAsc(shopOrderId,
                        List.of("CONFIRMED", "PREPARING", "READY_FOR_DELIVERY"))) {
            String statusLabel = switch (event.getStatus()) {
                case "CONFIRMED" -> "Cửa hàng đã xác nhận đơn hàng";
                case "PREPARING" -> "Cửa hàng đang chuẩn bị đơn hàng";
                case "READY_FOR_DELIVERY" -> "Sẵn sàng giao hàng";
                default -> OrderLabels.shop(event.getStatus());
            };
            history.add(new OrderProcessingHistoryEntry(statusLabel, OrderLabels.local(event.getCreatedAt()),
                    event.getNote()));
        }
        return history;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOrderForDeliveryCompletion(Long orderId) {
        Order order = entityManager.find(Order.class, orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
        }
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (order.getStatus() == Order.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể hoàn tất đơn hàng đã hủy.");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void startShopOrderDelivery(Long shopOrderId, Long changedBy) {
        ShopOrder shopOrder = shopOrderRepository.findById(shopOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        if (!"READY_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể bắt đầu giao hàng cho đơn hàng sẵn sàng giao hàng.");
        }
        shopOrder.markOutForDelivery();
        recordOrderStatus(shopOrder, changedBy);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeDeliveredShopOrder(Long shopOrderId, LocalDateTime completedAt, Long changedBy) {
        ShopOrder shopOrder = shopOrderRepository.findById(shopOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        Order order = shopOrder.getOrder();
        lockOrderForDeliveryCompletion(order.getOrderId());
        entityManager.refresh(shopOrder, LockModeType.PESSIMISTIC_WRITE);
        if (!"OUT_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể hoàn tất đơn hàng đang giao hàng.");
        }
        if (completedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa ghi nhận thời gian giao hàng thành công.");
        }
        boolean delivered = orderDeliveryReadRepository
                .findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(shopOrderId)).stream()
                .anyMatch(delivery -> "DELIVERED".equals(delivery.getStatus()) && delivery.getDeliveredAt() != null);
        if (!delivered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa xác nhận giao hàng thành công.");
        }

        shopOrder.markCompleted();
        if (paymentService.isCodOrder(order.getOrderId())) {
            shopOrder.setPaymentStatus("PAID");
        }
        if (shopOrder.getCompletedAt() == null) {
            shopOrder.setCompletedAt(completedAt);
        }
        recordOrderStatus(shopOrder, changedBy);
        shopOrderRepository.flush();
        if (shopOrderRepository.existsByOrder_OrderIdAndStatusNot(order.getOrderId(), "COMPLETED")) {
            order.setStatus(Order.Status.PARTIALLY_COMPLETED);
            order.setCompletedAt(null);
        } else {
            order.setStatus(Order.Status.COMPLETED);
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(completedAt);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateShopPaymentStatusesForVnpay(Long orderId, String paymentStatus) {
        if (!Set.of("PENDING", "PAID", "FAILED").contains(paymentStatus)) {
            throw new IllegalArgumentException("Trạng thái thanh toán VNPAY không hợp lệ.");
        }
        Order order = entityManager.find(Order.class, orderId);
        if (order == null || !paymentStatus.equals(order.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trạng thái lượt thanh toán chưa được xác nhận.");
        }
        for (ShopOrder shopOrder : shopOrderRepository.findByOrderOrderIdOrderByShopOrderIdAsc(orderId)) {
            if (!"CANCELLED".equals(shopOrder.getStatus())
                    && Set.of("UNPAID", "PENDING", "FAILED").contains(shopOrder.getPaymentStatus())) {
                shopOrder.setPaymentStatus(paymentStatus);
            }
        }
    }

    private OrderDetailResponse toOrderDetailResponse(ShopOrder shopOrder, OrderAddress address,
                                                     String shopAddress, String paymentMethod) {
        Order order = shopOrder.getOrder();
        LocalDateTime placedAt = OrderLabels.local(order.getPlacedAt());
        List<OrderItemResponse> items = new ArrayList<>();
        boolean deliveryFailed = "OUT_FOR_DELIVERY".equals(shopOrder.getStatus())
                && getFailedShopOrderIds(List.of(shopOrder.getShopOrderId())).contains(shopOrder.getShopOrderId());
        boolean deliveryTrackingAvailable = "READY_FOR_DELIVERY".equals(shopOrder.getStatus())
                || "COMPLETED".equals(shopOrder.getStatus()) || deliveryFailed;

        for (OrderItem item : shopOrder.getItems()) {
            items.add(new OrderItemResponse(
                    item.getProductNameSnapshot(),
                    item.getVariantNameSnapshot(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getTotalAmount()
            ));
        }

        return new OrderDetailResponse(
                shopOrder.getShopOrderCode(),
                placedAt,
                order.getCustomer().getFullName(),
                address == null ? null : address.getRecipientName(),
                address == null ? null : address.getPhone(),
                address == null ? null : formatDeliveryAddress(address),
                shopOrder.getStatus(),
                getOrderStatusLabel(shopOrder.getStatus(), deliveryFailed),
                getPaymentStatusLabel(shopOrder.getPaymentStatus()),
                shopOrder.getPaymentStatus(),
                shopOrder.getShippingFee(),
                shopOrder.getTotalAmount(),
                items,
                shopOrder.getShop().getShopName(),
                shopOrder.getShop().getPhone(),
                shopAddress,
                shopOrder.getSubtotal(),
                shopOrder.getDiscountTotal(),
                paymentMethod,
                formatInvoiceAmountInWords(shopOrder.getTotalAmount()),
                placedAt,
                deliveryTrackingAvailable
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
        ensureVnpayPaid(shopOrder);
        shopOrder.confirm();
        recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
        orderNotificationService.notifyConfirmed(shopOrder);
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
        recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
        orderNotificationService.notifyPreparing(shopOrder);
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
        recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
        deliveryCreationService.ensureDeliveryForReadyShopOrder(shopOrder);
        orderNotificationService.notifyReady(shopOrder);
    }

    private void recordOrderStatus(ShopOrder shopOrder, Long changedBy) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setShopOrder(shopOrder);
        history.setStatus(shopOrder.getStatus());
        history.setChangedBy(entityManager.getReference(User.class, changedBy));
        history.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        if ("CONFIRMED".equals(shopOrder.getStatus()) && shopOrder.getConfirmedAt() == null) {
            shopOrder.setConfirmedAt(history.getCreatedAt());
        }
        entityManager.persist(history);
    }

    private Page<ShopOrder> findFilteredShopOrders(Authentication authentication, OrderFilterRequest filter,
                                                 String view, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        }
        Specification<ShopOrder> specification = buildOrderSpecification(authentication, filter, view);
        if (filter.getSort() == null || filter.getSort().isBlank()) {
            filter.setSort("NEWEST");
        }
        int pageSize = "all".equals(view) || "processed".equals(view) ? 50 : 15;
        Pageable pageable = PageRequest.of(page, pageSize, resolveOrderSort(filter.getSort()));
        return shopOrderRepository.findAll(specification, pageable);
    }

    private Sort resolveOrderSort(String sort) {
        return switch (sort) {
            case "NEWEST" -> Sort.by(Sort.Direction.DESC, "order.placedAt", "shopOrderId");
            case "OLDEST" -> Sort.by(Sort.Direction.ASC, "order.placedAt", "shopOrderId");
            case "PRICE_HIGH" -> Sort.by(Sort.Direction.DESC, "totalAmount", "shopOrderId");
            case "PRICE_LOW" -> Sort.by(Sort.Direction.ASC, "totalAmount", "shopOrderId");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lựa chọn sắp xếp đơn hàng không hợp lệ.");
        };
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
            case "failed" -> List.of("OUT_FOR_DELIVERY");
            case "cancelled" -> List.of("CANCELLED");
            case "processed" -> List.of();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách đơn hàng không hợp lệ.");
        };
        boolean filterStatus = "all".equals(view) || "processed".equals(view) || "failed".equals(view);
        String selectedStatus = "failed".equals(view) ? "DELIVERY_FAILED" : filter.getStatus();

        return (root, query, builder) -> {
            Join<ShopOrder, Order> order = root.join("order", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("shopId"), shopId));

            if ("processed".equals(view)) {
                Predicate returnedFailure = deliveryReturnQueryService.confirmedReturnedFailures()
                        .toPredicate(root, query, builder);
                predicates.add(builder.or(root.get("status").in("COMPLETED", "CANCELLED"), returnedFailure));
            } else if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (filterStatus && selectedStatus != null && !selectedStatus.isBlank()) {
                String status = selectedStatus.trim();
                if ("processed".equals(view) && "RETURNED_FAILED".equals(status)) {
                    predicates.add(builder.equal(root.get("status"), "OUT_FOR_DELIVERY"));
                } else if ("DELIVERY_FAILED".equals(status) || "OUT_FOR_DELIVERY".equals(status)) {
                    predicates.add(builder.equal(root.get("status"), "OUT_FOR_DELIVERY"));
                    Subquery<Long> failedDelivery = query.subquery(Long.class);
                    Root<OrderDeliveryView> delivery = failedDelivery.from(OrderDeliveryView.class);
                    failedDelivery.select(delivery.get("deliveryId")).where(
                            builder.equal(delivery.get("shopOrder").get("shopOrderId"), root.get("shopOrderId")),
                            builder.equal(delivery.get("status"), "FAILED")
                    );
                    Predicate deliveryFailed = builder.exists(failedDelivery);
                    predicates.add("DELIVERY_FAILED".equals(status) ? deliveryFailed : builder.not(deliveryFailed));
                } else {
                    predicates.add(builder.equal(root.get("status"), status));
                }
            }
            if (filter.getFromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(order.get("placedAt"), toUtcStartOfDay(filter.getFromDate())));
            }
            if (filter.getToDate() != null) {
                predicates.add(builder.lessThan(order.get("placedAt"), toUtcStartOfDay(filter.getToDate().plusDays(1))));
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
                predicates.add(builder.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String keyword = filter.getKeyword().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
                String pattern = "%" + keyword + "%";
                Long shopOrderCode = VietFreshHub.common.BusinessCodes.parse("VFH", filter.getKeyword());
                if (shopOrderCode == null) shopOrderCode = VietFreshHub.common.BusinessCodes.parse("SOR", filter.getKeyword());
                List<Predicate> references = new ArrayList<>();
                references.add(builder.like(builder.lower(order.get("orderCode")), pattern, '\\'));
                references.add(builder.like(builder.lower(order.join("customer", JoinType.LEFT).get("fullName")), pattern, '\\'));
                if (shopOrderCode != null) references.add(builder.equal(root.get("shopOrderId"), shopOrderCode));
                predicates.add(builder.or(references.toArray(new Predicate[0])));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private LocalDateTime toUtcStartOfDay(LocalDate date) {
        LocalDateTime utc = date.atStartOfDay(VIETNAM_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return utc.getYear() < 1 ? LocalDateTime.of(1, 1, 1, 0, 0) : utc;
    }

    private void validateDateRange(OrderFilterRequest filter) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
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
        return toOrderResponse(shopOrder, false);
    }

    private IncomingOrderResponse toOrderResponse(ShopOrder shopOrder, boolean deliveryFailed) {
        Order order = shopOrder.getOrder();
        return new IncomingOrderResponse(
                shopOrder.getShopOrderId(),
                shopOrder.getShopOrderCode(),
                order.getCustomer().getFullName(),
                OrderLabels.local(order.getPlacedAt()),
                shopOrder.getTotalAmount(),
                shopOrder.getStatus(),
                getOrderStatusLabel(shopOrder.getStatus(), deliveryFailed),
                shopOrder.getPaymentStatus()
        );
    }

    private Set<Long> getFailedShopOrderIds(List<Long> shopOrderIds) {
        if (shopOrderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(orderDeliveryReadRepository.findFailedShopOrderIds(shopOrderIds));
    }

    private ShopOrder getShopOrderForUpdate(Long shopOrderId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        if (shopOrderId == null || shopOrderId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng được chọn không hợp lệ.");
        }
        ShopOrder shopOrder = shopOrderRepository.findById(shopOrderId)
                .filter(value -> shopId.equals(value.getShopId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Order order = entityManager.getReference(Order.class, shopOrder.getOrder().getOrderId());
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (order.getStatus() == Order.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xử lý đơn hàng đã hủy.");
        }
        shopOrder = shopOrderRepository.findForUpdateByShopOrderIdAndShopId(shopOrderId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.refresh(shopOrder, LockModeType.PESSIMISTIC_WRITE);
        ensureVnpayPaid(shopOrder);
        return shopOrder;
    }

    private void ensureVnpayPaid(ShopOrder shopOrder) {
        Order order = shopOrder.getOrder();
        Long attempts = entityManager.createQuery("select count(p) from Payment p where p.order.orderId=:orderId "
                        + "and (p.provider='VNPAY' or p.paymentMethod.methodCode='VNPAY')", Long.class)
                .setParameter("orderId", order.getOrderId()).getSingleResult();
        if (attempts > 0 && !"PAID".equals(shopOrder.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cửa hàng chỉ có thể xác nhận đơn VNPAY sau khi thanh toán được xác nhận.");
        }
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

    private String formatInvoiceShopAddress(ShopLocation location) {
        if (location == null) {
            return null;
        }
        if (location.getFormattedAddress() != null && !location.getFormattedAddress().isBlank()) {
            return location.getFormattedAddress();
        }
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{location.getAddressLine(), location.getWard(),
                location.getDistrict(), location.getProvince()}) {
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return String.join(", ", parts);
    }

    private String formatInvoiceAmountInWords(BigDecimal amount) {
        long remaining = amount.abs().longValue();
        List<String> parts = new ArrayList<>();
        int scale = 0;
        while (remaining > 0) {
            int group = (int) (remaining % 1000);
            if (group > 0) {
                String groupWords = readInvoiceNumberGroup(group, remaining >= 1000);
                if (scale > 0) {
                    groupWords += " " + INVOICE_NUMBER_SCALES[scale];
                }
                parts.addFirst(groupWords);
            }
            remaining /= 1000;
            scale++;
        }
        String words = parts.isEmpty() ? VIETNAMESE_DIGITS[0] : String.join(" ", parts);
        String decimalAmount = amount.abs().stripTrailingZeros().toPlainString();
        int decimalPoint = decimalAmount.indexOf('.');
        if (decimalPoint >= 0) {
            words += " phẩy";
            for (int index = decimalPoint + 1; index < decimalAmount.length(); index++) {
                words += " " + VIETNAMESE_DIGITS[decimalAmount.charAt(index) - '0'];
            }
        }
        if (amount.signum() < 0) {
            words = "âm " + words;
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " đồng.";
    }

    private String readInvoiceNumberGroup(int number, boolean fullHundreds) {
        int hundreds = number / 100;
        int tens = number / 10 % 10;
        int units = number % 10;
        List<String> words = new ArrayList<>();
        if (hundreds > 0 || fullHundreds) {
            words.add(VIETNAMESE_DIGITS[hundreds] + " trăm");
        }
        if (tens > 1) {
            words.add(VIETNAMESE_DIGITS[tens] + " mươi");
        } else if (tens == 1) {
            words.add("mười");
        } else if (units > 0 && !words.isEmpty()) {
            words.add("lẻ");
        }
        if (units == 1 && tens > 1) {
            words.add("mốt");
        } else if (units == 5 && tens > 0) {
            words.add("lăm");
        } else if (units > 0) {
            words.add(VIETNAMESE_DIGITS[units]);
        }
        return String.join(" ", words);
    }

    private String getOrderStatusLabel(String status, boolean deliveryFailed) {
        if ("OUT_FOR_DELIVERY".equals(status) && deliveryFailed) {
            return "Giao hàng thất bại";
        }
        return getOrderStatusLabel(status);
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
