package VietFreshHub.order.service.impl;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.service.DeliveryCreationService;
import VietFreshHub.order.dto.IncomingOrderResponse;
import VietFreshHub.order.dto.OrderCountsResponse;
import VietFreshHub.order.dto.OrderDetailResponse;
import VietFreshHub.order.dto.OrderFilterRequest;
import VietFreshHub.order.dto.OrderItemResponse;
import VietFreshHub.order.dto.ProcessedOrderResponse;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.OrderDeliveryView;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderDeliveryReadRepository;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.order.service.OrderService;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ShopOrderRepository shopOrderRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final OrderDeliveryReadRepository orderDeliveryReadRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopService shopService;
    private final DeliveryCreationService deliveryCreationService;
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
                shopOrderRepository.count(buildOrderSpecification(authentication, new OrderFilterRequest(), "incoming")),
                shopOrderRepository.countByShopIdAndStatus(shopId, "CONFIRMED"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "PREPARING"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "READY_FOR_DELIVERY"),
                shopOrderRepository.countByShopIdAndStatus(shopId, "COMPLETED")
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

        shopOrder.markCompleted();
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

    private OrderDetailResponse toOrderDetailResponse(ShopOrder shopOrder) {
        Order order = shopOrder.getOrder();
        OrderAddress address = orderAddressRepository.findByOrderId(order.getOrderId()).orElse(null);
        List<OrderItemResponse> items = new ArrayList<>();
        boolean deliveryFailed = "OUT_FOR_DELIVERY".equals(shopOrder.getStatus())
                && getFailedShopOrderIds(List.of(shopOrder.getShopOrderId())).contains(shopOrder.getShopOrderId());

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
                getOrderStatusLabel(shopOrder.getStatus(), deliveryFailed),
                getPaymentStatusLabel(order.getPaymentStatus()),
                order.getPaymentStatus(),
                shopOrder.getShippingFee(),
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
        ensureVnpayPaid(shopOrder.getOrder());
        shopOrder.confirm();
        recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
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
            ensureVnpayPaid(shopOrder.getOrder());
        }
        for (ShopOrder shopOrder : shopOrders) {
            shopOrder.confirm();
            recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
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
            recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
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
            recordOrderStatus(shopOrder, authService.getCurrentUserId(authentication));
            deliveryCreationService.ensureDeliveryForReadyShopOrder(shopOrder);
        }
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
        List<Long> selectedIds = validateSelectedIds(shopOrderIds);
        TreeSet<Long> orderIds = new TreeSet<>();
        for (Long shopOrderId : selectedIds) {
            ShopOrder shopOrder = shopOrderRepository.findById(shopOrderId)
                    .filter(value -> shopId.equals(value.getShopId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            orderIds.add(shopOrder.getOrder().getOrderId());
        }
        for (Long orderId : orderIds) {
            Order order = entityManager.getReference(Order.class, orderId);
            entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
            if (order.getStatus() == Order.Status.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xử lý đơn hàng đã hủy.");
            }
            ensureVnpayPaid(order);
        }
        List<ShopOrder> shopOrders = new ArrayList<>();
        for (Long shopOrderId : selectedIds) {
            ShopOrder shopOrder = shopOrderRepository.findForUpdateByShopOrderIdAndShopId(shopOrderId, shopId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            entityManager.refresh(shopOrder, LockModeType.PESSIMISTIC_WRITE);
            shopOrders.add(shopOrder);
        }
        return shopOrders;
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
                String status = filter.getStatus().trim();
                if ("DELIVERY_FAILED".equals(status) || "OUT_FOR_DELIVERY".equals(status)) {
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
                Long shopOrderCode = VietFreshHub.common.BusinessCodes.parse("SOR", filter.getKeyword());
                List<Predicate> references = new ArrayList<>();
                references.add(builder.like(builder.lower(order.get("orderCode")), pattern, '\\'));
                references.add(builder.like(builder.lower(order.join("customer", JoinType.LEFT).get("fullName")), pattern, '\\'));
                if (shopOrderCode != null) references.add(builder.equal(root.get("shopOrderId"), shopOrderCode));
                predicates.add(builder.or(references.toArray(new Predicate[0])));
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
        return toOrderResponse(shopOrder, false);
    }

    private IncomingOrderResponse toOrderResponse(ShopOrder shopOrder, boolean deliveryFailed) {
        Order order = shopOrder.getOrder();
        return new IncomingOrderResponse(
                shopOrder.getShopOrderId(),
                order.getOrderCode(),
                order.getCustomer().getFullName(),
                order.getPlacedAt(),
                shopOrder.getTotalAmount(),
                shopOrder.getStatus(),
                getOrderStatusLabel(shopOrder.getStatus(), deliveryFailed),
                order.getPaymentStatus()
        );
    }

    private Set<Long> getFailedShopOrderIds(List<Long> shopOrderIds) {
        if (shopOrderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(orderDeliveryReadRepository.findFailedShopOrderIds(shopOrderIds));
    }

    private ShopOrder getShopOrderForUpdate(Long shopOrderId, Authentication authentication) {
        return getShopOrdersForUpdate(List.of(shopOrderId), authentication).getFirst();
    }

    private void ensureVnpayPaid(Order order) {
        Long attempts = entityManager.createQuery("select count(p) from Payment p where p.order.orderId=:orderId "
                        + "and (p.provider='VNPAY' or p.paymentMethod.methodCode='VNPAY')", Long.class)
                .setParameter("orderId", order.getOrderId()).getSingleResult();
        if (attempts > 0 && !"PAID".equals(order.getPaymentStatus())) {
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
