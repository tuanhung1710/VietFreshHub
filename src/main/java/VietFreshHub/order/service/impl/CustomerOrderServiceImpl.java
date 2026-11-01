package VietFreshHub.order.service.impl;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.order.service.OrderCancelledEvent;
import VietFreshHub.order.service.OrderLabels;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.repository.CartItemRepository;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.order.dto.CustomerOrderFilterRequest;
import VietFreshHub.order.dto.CustomerOrderStage;
import VietFreshHub.order.dto.OrderListRow;
import VietFreshHub.order.dto.OrderHistorySummary;
import VietFreshHub.order.dto.OrderActionsResponse;
import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.dto.OrderTrackingResponse;
import VietFreshHub.order.dto.ReorderResult;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.OrderDeliveryView;
import VietFreshHub.order.entity.OrderDeliveryHistoryView;
import VietFreshHub.order.entity.Order;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.order.repository.OrderDeliveryReadRepository;
import VietFreshHub.order.repository.OrderDeliveryHistoryReadRepository;
import VietFreshHub.order.repository.OrderStatusHistoryRepository;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.catalog.entity.CatalogVariant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class CustomerOrderServiceImpl implements CustomerOrderService {
    private final OrderRepository orders;
    private final ShopOrderRepository shopOrders;
    private final PaymentRepository payments;
    private final OrderDeliveryReadRepository deliveries;
    private final OrderDeliveryHistoryReadRepository deliveryHistory;
    private final OrderStatusHistoryRepository orderHistory;
    private final DeliveryAssignmentService deliveryAssignments;
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CatalogVariantRepository variants;
    private final CatalogService catalog;
    private final CheckoutInventoryBatchRepository stock;
    private final OrderInventoryReleaseService inventory;
    private final EntityManager em;
    private final ApplicationEventPublisher events;


    @Override
    @Transactional(readOnly = true)
    public Page<OrderListRow> history(Long customerId, CustomerOrderFilterRequest filter) {
        validateHistoryFilter(filter);
        String statusText = "";
        if (filter.getStatus() != null) {
            statusText = filter.getStatus().trim();
        }
        String status = historyStatus(statusText);
        filter.setKeyword(filter.getKeyword().trim());
        filter.setStatus(statusText);

        ZoneId customerZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime from = historyStartDate(filter.getFromDate(), customerZone);
        LocalDateTime until = historyEndDateExclusive(filter.getToDate(), customerZone);
        Sort sort = Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("orderId"));
        PageRequest requestedPage = PageRequest.of(filter.getPage() - 1, 10, sort);
        Page<OrderListRow> result = orders.findHistory(customerId, status, filter.getKeyword(), from, until, requestedPage);
        if (result.getTotalElements() == 0) {
            return Page.empty(PageRequest.of(0, 10, sort));
        }
        if (result.getNumber() >= result.getTotalPages()) {
            PageRequest lastPage = PageRequest.of(result.getTotalPages() - 1, 10, sort);
            return orders.findHistory(customerId, status, filter.getKeyword(), from, until, lastPage);
        }
        return result;
    }

    private void validateHistoryFilter(CustomerOrderFilterRequest filter) {
        if (filter == null || filter.getPage() < 1 || filter.getPage() > 10000 || filter.getKeyword() == null
                || filter.getKeyword().length() > 100 || !filter.isDateRangeValid() || !filter.isDatabaseDateRangeValid()) {
            throw new CustomerOrderException("Bộ lọc đơn hàng không hợp lệ.");
        }
    }

    private String historyStatus(String statusText) {
        try {
            if (statusText.isEmpty()) {
                return "";
            }
            return CustomerOrderStage.valueOf(statusText).name();
        } catch (IllegalArgumentException exception) {
            throw new CustomerOrderException("Trạng thái lọc không hợp lệ.");
        }
    }

    private LocalDateTime historyStartDate(LocalDate date, ZoneId customerZone) {
        if (date == null) {
            return null;
        }
        LocalDateTime from = LocalDateTime.ofInstant(date.atStartOfDay(customerZone).toInstant(), ZoneOffset.UTC);
        // Giữ giới hạn DATETIME2 cũ khi đổi múi giờ tạo ra năm 0.
        if (from.getYear() < 1) {
            return LocalDateTime.of(1, 1, 1, 0, 0);
        }
        return from;
    }

    private LocalDateTime historyEndDateExclusive(LocalDate date, ZoneId customerZone) {
        if (date == null || date.equals(LocalDate.of(9999, 12, 31))) {
            return null;
        }
        return LocalDateTime.ofInstant(date.plusDays(1).atStartOfDay(customerZone).toInstant(), ZoneOffset.UTC);
    }


    /** Giữ bốn lần đọc theo nhóm cho một trang lịch sử, không query riêng cho từng đơn. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, OrderHistorySummary> historySummaries(Long customerId, List<Long> orderIds) {
        validateHistoryOrderIds(customerId, orderIds);
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Order> parents = orders.findCustomerHistoryOrders(customerId, orderIds);
        if (parents.isEmpty()) {
            return Map.of();
        }
        List<Long> ownedIds = new ArrayList<>();
        for (Order parent : parents) {
            ownedIds.add(parent.getOrderId());
        }
        Map<Long, List<ShopOrder>> shopsByOrder = new HashMap<>();
        for (ShopOrder shop : shopOrders.findCustomerHistoryShops(customerId, ownedIds)) {
            shopsByOrder.computeIfAbsent(shop.getOrder().getOrderId(), key -> new ArrayList<>()).add(shop);
        }
        Map<Long, List<Payment>> paymentsByOrder = new HashMap<>();
        for (Payment payment : payments.findCustomerHistoryPayments(customerId, ownedIds)) {
            paymentsByOrder.computeIfAbsent(payment.getOrder().getOrderId(), key -> new ArrayList<>()).add(payment);
        }
        Map<Long, List<OrderDeliveryView>> deliveriesByOrder = new HashMap<>();
        for (OrderDeliveryView delivery : deliveries.findCustomerHistoryDeliveries(customerId, ownedIds)) {
            Long orderId = delivery.getShopOrder().getOrder().getOrderId();
            deliveriesByOrder.computeIfAbsent(orderId, key -> new ArrayList<>()).add(delivery);
        }

        Map<Long, OrderHistorySummary> result = new LinkedHashMap<>();
        for (Order parent : parents) {
            Long orderId = parent.getOrderId();
            List<ShopOrder> shops = shopsByOrder.getOrDefault(orderId, List.of());
            List<Payment> paymentRows = paymentsByOrder.getOrDefault(orderId, List.of());
            List<OrderDeliveryView> deliveryRows = deliveriesByOrder.getOrDefault(orderId, List.of());
            List<OrderItem> lines = sortedHistoryItems(shops);
            long unitCount = 0;
            for (OrderItem line : lines) {
                unitCount += line.getQuantity();
            }
            OrderHistorySummary summary = new OrderHistorySummary(
                    historyProducts(lines), lines.size(), unitCount,
                    historyShops(shops, deliveryRows), historyPayments(paymentRows), paymentRows.size(),
                    cancellationHint(parent, shops, deliveryRows) == null, !lines.isEmpty(), !shops.isEmpty());
            result.put(orderId, summary);
        }
        return Map.copyOf(result);
    }

    private void validateHistoryOrderIds(Long customerId, List<Long> orderIds) {
        if (customerId == null || customerId < 1 || orderIds == null || orderIds.size() > 10) {
            throw new CustomerOrderException("Danh sách đơn hàng không hợp lệ.");
        }
        for (Long orderId : orderIds) {
            if (orderId == null || orderId < 1) {
                throw new CustomerOrderException("Danh sách đơn hàng không hợp lệ.");
            }
        }
    }

    private List<OrderItem> sortedHistoryItems(List<ShopOrder> shops) {
        List<OrderItem> lines = new ArrayList<>();
        for (ShopOrder shop : shops) {
            lines.addAll(shop.getItems());
        }
        lines.sort(Comparator.comparing(OrderItem::getOrderItemId));
        return lines;
    }

    private List<OrderHistorySummary.Product> historyProducts(List<OrderItem> lines) {
        List<OrderHistorySummary.Product> products = new ArrayList<>();
        int previewCount = Math.min(3, lines.size());
        for (int index = 0; index < previewCount; index++) {
            OrderItem item = lines.get(index);
            String image = null;
            if (item.getVariant() != null) {
                image = item.getVariant().getThumbnailUrl();
            }
            if (image == null || image.isBlank()) {
                image = null;
            }
            products.add(new OrderHistorySummary.Product(item.getProductNameSnapshot(), item.getVariantNameSnapshot(),
                    item.getQuantity(), image));
        }
        return products;
    }

    private List<OrderHistorySummary.Shop> historyShops(List<ShopOrder> shops, List<OrderDeliveryView> deliveries) {
        List<OrderHistorySummary.Shop> result = new ArrayList<>();
        Map<Long, OrderDeliveryView> firstDeliveryByShop = new HashMap<>();
        for (OrderDeliveryView delivery : deliveries) {
            firstDeliveryByShop.putIfAbsent(delivery.getShopOrder().getShopOrderId(), delivery);
        }
        for (ShopOrder shop : shops) {
            OrderDeliveryView matchedDelivery = firstDeliveryByShop.get(shop.getShopOrderId());
            String fulfillmentLabel = OrderLabels.shop(shop.getStatus());
            String deliveryStatus = null;
            String deliveryLabel = null;
            if (matchedDelivery != null) {
                if ("OUT_FOR_DELIVERY".equals(shop.getStatus())) {
                    fulfillmentLabel = "Vận chuyển";
                }
                deliveryStatus = matchedDelivery.getStatus();
                deliveryLabel = historyDeliveryLabel(deliveryStatus);
            }
            result.add(new OrderHistorySummary.Shop(shop.getShop().getShopName(), fulfillmentLabel, deliveryStatus, deliveryLabel));
        }
        return result;
    }

    private List<OrderDetailsResponse.PaymentInfo> historyPayments(List<Payment> payments) {
        List<Payment> sortedPayments = new ArrayList<>(payments);
        sortedPayments.sort(Comparator.comparing(Payment::getPaymentId).reversed());
        List<OrderDetailsResponse.PaymentInfo> result = new ArrayList<>();
        int previewCount = Math.min(3, sortedPayments.size());
        for (int index = 0; index < previewCount; index++) {
            Payment payment = sortedPayments.get(index);
            result.add(new OrderDetailsResponse.PaymentInfo(payment.getPaymentId(), payment.getPaymentMethod().getName(),
                    payment.getPaymentMethod().getMethodCode(), payment.getStatus().name(),
                    OrderLabels.paymentTransaction(payment.getStatus(), payment.getPaymentMethod().getMethodCode()),
                    payment.getAmount(), OrderLabels.local(payment.getPaidAt())));
        }
        return result;
    }

    private String historyDeliveryLabel(String status) {
        return switch (status) {
            case "OUT_FOR_DELIVERY" -> "Đang giao";
            case "FAILED" -> "Giao hàng thất bại";
            case "REDELIVERY_SCHEDULED" -> "Chờ giao lại";
            case "DELIVERED" -> "Đã giao hàng";
            default -> OrderLabels.delivery(status);
        };
    }


    @Override
    @Transactional(readOnly = true)
    public OrderActionsResponse actions(Long customerId, Long orderId) {
        Order order = owned(customerId, orderId);
        List<ShopOrder> children = shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        List<Payment> paymentRows = payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        List<OrderDeliveryView> deliveryRows = deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        String hint = cancellationHint(order, children, deliveryRows);
        String paymentMethod = "Chưa có phương thức thanh toán";
        if (!paymentRows.isEmpty()) {
            paymentMethod = paymentRows.getFirst().getPaymentMethod().getName();
        }
        return new OrderActionsResponse(hint == null, hint, paymentMethod);
    }

    @Override
    @Transactional
    public void cancel(Long customerId, Long orderId, String reason) {
        if (reason != null && reason.length() > 500) {
            throw new CustomerOrderException("Lý do hủy tối đa 500 ký tự.");
        }
        User actor = lockCustomer(customerId);
        Order order = owned(customerId, orderId);
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (order.getStatus() == Order.Status.CANCELLED) {
            return;
        }
        List<ShopOrder> children = shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        for (ShopOrder child : children) {
            em.refresh(child, LockModeType.PESSIMISTIC_WRITE);
        }
        List<Payment> paymentRows = payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        for (Payment payment : paymentRows) {
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        }
        List<OrderDeliveryView> deliveryRows = deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        for (OrderDeliveryView delivery : deliveryRows) {
            em.refresh(delivery, LockModeType.PESSIMISTIC_READ);
        }
        String hint = cancellationHint(order, children, deliveryRows);
        if (hint != null) {
            throw new CustomerOrderException(hint);
        }
        List<OrderInventoryReleaseService.Line> lines = cancellationLines(children);
        inventory.release(lines, customerId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        String note = "Khách hàng hủy đơn.";
        if (reason != null && !reason.isBlank()) {
            note += " Lý do: " + reason.trim();
        }
        recordCancelledShops(children, actor, now, note);
        // Chỉ hủy các lần thanh toán chưa thu; không thực hiện hoàn tiền gateway.
        for (Payment payment : paymentRows) {
            if (payment.getPaidAt() == null
                    && (payment.getStatus() == Payment.Status.PENDING || payment.getStatus() == Payment.Status.FAILED)) {
                payment.setStatus(Payment.Status.CANCELLED);
            }
        }
        order.setStatus(Order.Status.CANCELLED);
        order.setCancelledAt(now);
        em.flush();
        events.publishEvent(new OrderCancelledEvent(orderId, customerId));
    }

    private List<OrderInventoryReleaseService.Line> cancellationLines(List<ShopOrder> children) {
        List<OrderInventoryReleaseService.Line> lines = new ArrayList<>();
        for (ShopOrder child : children) {
            for (OrderItem item : child.getItems()) {
                lines.add(new OrderInventoryReleaseService.Line(
                        item.getOrderItemId(), item.getVariant().getVariantId(), item.getQuantity()));
            }
        }
        return List.copyOf(lines);
    }

    private void recordCancelledShops(List<ShopOrder> children, User actor, LocalDateTime now, String note) {
        for (ShopOrder child : children) {
            if (!"CANCELLED".equals(child.getStatus())) {
                child.setStatus("CANCELLED");
                child.setCancelledAt(now);
                OrderStatusHistory history = new OrderStatusHistory();
                history.setShopOrder(child);
                history.setStatus("CANCELLED");
                history.setChangedBy(actor);
                history.setNote(note);
                em.persist(history);
            }
        }
    }

    private String cancellationHint(Order order,List<ShopOrder> children,List<OrderDeliveryView> delivery) {
        if(order.getStatus()==Order.Status.CANCELLED) return "Đơn hàng đã được hủy.";
        if(children.isEmpty()) return "Đơn hàng chưa có thông tin cửa hàng, không thể hủy.";
        if(children.stream().anyMatch(c -> "CANCELLED".equals(c.getStatus())))
            return "Một đơn cửa hàng đã được hủy. Không thể hủy lại hoặc hủy một phần đơn này.";
        // UC-22 / GBR-25: confirmation still allows cancellation; preparation does not.
        for (ShopOrder child : children) {
            if (!"PENDING".equals(child.getStatus()) && !"CONFIRMED".equals(child.getStatus())) {
                return "Một cửa hàng đã bắt đầu chuẩn bị hoặc xử lý đơn. Bạn không thể hủy toàn bộ đơn này.";
            }
        }
        if(delivery.stream().anyMatch(d -> !Set.of("UNASSIGNED","ASSIGNED","ACCEPTED","CANCELLED").contains(d.getStatus())))
            return "Đơn đã được lấy hoặc đang giao, không thể hủy.";
        // Group policy: eligibility follows every shop's fulfillment progress, not
        // payment method, payment state, discounts, or an aggregate parent that may lag.
        return null;
    }

    @Override
    @Transactional
    public ReorderResult reorder(Long customerId, Long orderId) {
        User buyer = lockCustomer(customerId);
        owned(customerId, orderId);
        List<ShopOrder> children = shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        Map<Long, Long> quantities = new TreeMap<>();
        Map<Long, OrderItem> originalItems = new HashMap<>();
        for (ShopOrder child : children) {
            for (OrderItem item : child.getItems()) {
                Long variantId = item.getVariant().getVariantId();
                quantities.merge(variantId, item.getQuantity().longValue(), Long::sum);
                originalItems.putIfAbsent(variantId, item);
            }
        }

        Cart cart = carts.findByUserUserIdAndStatus(customerId, "ACTIVE").orElse(null);
        List<ReorderResult.SkippedItem> skipped = new ArrayList<>();
        int addedLines = 0;
        long addedUnits = 0;
        for (Map.Entry<Long, Long> entry : quantities.entrySet()) {
            Long variantId = entry.getKey();
            long requestedQuantity = entry.getValue();
            OrderItem original = originalItems.get(variantId);
            CatalogVariant variant = variants.findById(variantId).orElse(null);
            CartItem existing = null;
            if (cart != null) {
                existing = cartItems.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId).orElse(null);
            }
            long desiredQuantity = requestedQuantity;
            if (existing != null) {
                desiredQuantity += existing.getQuantity();
            }

            String rejection = null;
            if (variant == null || !catalog.isPurchasable(variant)) {
                rejection = "Sản phẩm hoặc cửa hàng hiện không nhận đơn.";
            } else if (requestedQuantity < 1 || desiredQuantity > Integer.MAX_VALUE
                    || desiredQuantity > stock.getAvailableStockByVariantId(variantId)) {
                rejection = "Không đủ hàng cho số lượng mua lại và số lượng đang có trong giỏ.";
            }
            if (rejection != null) {
                skipped.add(new ReorderResult.SkippedItem(original.getProductNameSnapshot(),
                        original.getVariantNameSnapshot(), rejection));
                continue;
            }

            if (cart == null) {
                cart = new Cart();
                cart.setUser(buyer);
                carts.save(cart);
            }
            if (existing == null) {
                existing = new CartItem();
                existing.setCart(cart);
                existing.setVariant(variant);
            }
            existing.setQuantity((int) desiredQuantity);
            existing.setUnitPriceSnapshot(variant.getPrice());
            cartItems.save(existing);
            addedLines++;
            addedUnits += requestedQuantity;
        }
        return new ReorderResult(addedLines, addedUnits, List.copyOf(skipped));
    }

    @Override @Transactional(readOnly=true)
    public OrderTrackingResponse tracking(Long customerId,Long orderId) {
        Order order=owned(customerId,orderId); var children=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        var rows=deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        var history=rows.isEmpty() ? List.<OrderDeliveryHistoryView>of() : deliveryHistory.findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(rows.stream().map(OrderDeliveryView::getDeliveryId).toList());
        var statuses=orderHistory.findByShopOrderOrderOrderIdOrderByCreatedAtAscHistoryIdAsc(orderId);
        var staff=rows.isEmpty() ? List.<DeliveryTrackingStaff>of() : deliveryAssignments.getTrackingStaff(customerId,orderId);
        var groups=children.stream().map(child -> {
            OrderDeliveryView delivery=rows.stream().filter(d -> d.getShopOrder().getShopOrderId().equals(child.getShopOrderId())).findFirst().orElse(null);
            var shopHistory=statuses.stream().filter(h -> h.getShopOrder().getShopOrderId().equals(child.getShopOrderId())).toList();
            var deliveryEvents=history.stream().filter(h -> delivery!=null && h.getDelivery().getDeliveryId().equals(delivery.getDeliveryId())).toList();
            var deliveryStates=deliveryEvents.stream().map(OrderDeliveryHistoryView::getStatus).toList();
            List<OrderTrackingResponse.Event> timeline=new ArrayList<>();
            shopHistory.stream()
                    .filter(h -> !"OUT_FOR_DELIVERY".equals(h.getStatus()) || !deliveryStates.contains("OUT_FOR_DELIVERY"))
                    .filter(h -> !"COMPLETED".equals(h.getStatus()) || !deliveryStates.contains("DELIVERED"))
                    .forEach(h -> timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(h.getCreatedAt()),trackingShopLabel(h.getStatus()),h.getNote(),"ORDER")));
            if(delivery!=null) {
                for(var event:deliveryEvents) {
                    String staffName=event.getChangedBy()==null ? null : trackingStaffName(staff,delivery.getDeliveryId(),event.getChangedBy());
                    String eventLabel=trackingDeliveryLabel(event.getStatus(),staffName);
                    if(eventLabel!=null) timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(event.getCreatedAt()),eventLabel,null,"DELIVERY"));
                }
            }
            addMissingConfirmation(child,shopHistory,timeline);
            // Historical data may lack timestamps; never invent a time for those events.
            timeline.sort(Comparator.comparing(OrderTrackingResponse.Event::occurredAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            String label=trackingShopLabel(child.getStatus());
            String deliveryStatusCode=child.getStatus();
            if(delivery!=null && !"CANCELLED".equals(child.getStatus())
                    && !Set.of("UNASSIGNED","ASSIGNED").contains(delivery.getStatus())) {
                String staffName=trackingStaffName(staff,delivery.getDeliveryId(),null);
                String deliveryLabel=trackingDeliveryLabel(delivery.getStatus(),staffName);
                label=deliveryLabel==null ? "Thông tin giao hàng cần được kiểm tra." : deliveryLabel;
                deliveryStatusCode=deliveryLabel==null ? null : delivery.getStatus();
            }
            String failureReason = currentFailureReason(child, delivery);
            return new OrderTrackingResponse.ShopTracking(child.getShopOrderId(),child.getShop().getShopName(),trackingShopLabel(child.getStatus()),
                    child.getStatus(),label,deliveryStatusCode,failureReason,List.copyOf(timeline));
        }).toList();
        return new OrderTrackingResponse(orderId,order.getOrderCode(),OrderLabels.order(order.getStatus()),order.getStatus().name(),groups);
    }
    private String currentFailureReason(ShopOrder child, OrderDeliveryView delivery) {
        // The delivery stores the current reason, not a reason snapshot for each historical attempt.
        if (delivery == null || !"FAILED".equals(delivery.getStatus()) || "CANCELLED".equals(child.getStatus())) {
            return null;
        }
        String reason = delivery.getFailureReason();
        return reason == null || reason.isBlank() ? null : reason.strip();
    }

    private void addMissingConfirmation(ShopOrder child,List<OrderStatusHistory> history,List<OrderTrackingResponse.Event> timeline) {
        if(history.stream().anyMatch(entry -> "CONFIRMED".equals(entry.getStatus()))) return;
        Set<String> confirmedStates=Set.of("CONFIRMED","PREPARING","READY_FOR_DELIVERY","OUT_FOR_DELIVERY","COMPLETED");
        boolean confirmed=child.getConfirmedAt()!=null || confirmedStates.contains(child.getStatus())
                || history.stream().anyMatch(entry -> confirmedStates.contains(entry.getStatus()));
        if(!confirmed) return;
        var confirmation=new OrderTrackingResponse.Event(OrderLabels.local(child.getConfirmedAt()),trackingShopLabel("CONFIRMED"),null,"ORDER",child.getConfirmedAt()!=null);
        int position=0;
        for(int i=0;i<timeline.size();i++) {
            var entry=timeline.get(i);
            if("ORDER".equals(entry.kind()) && trackingShopLabel("PENDING").equals(entry.label())) position=i+1;
        }
        timeline.add(position,confirmation);
    }
    private String trackingStaffName(List<DeliveryTrackingStaff> staff,Long deliveryId,Long staffId) {
        return staff.stream().filter(s -> s.deliveryId().equals(deliveryId) && (staffId==null || s.staffId().equals(staffId)))
                .findFirst().map(DeliveryTrackingStaff::staffName).orElse(null);
    }
    private String trackingShopLabel(String status) {
        return switch(status) {
            case "PENDING" -> "Chờ cửa hàng xác nhận đơn hàng";
            case "CONFIRMED" -> "Cửa hàng đã xác nhận đơn hàng";
            case "PREPARING" -> "Cửa hàng đang chuẩn bị đơn hàng";
            case "READY_FOR_DELIVERY" -> "Sẵn sàng giao hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "CANCELLED" -> "Đã hủy đơn hàng";
            default -> OrderLabels.shop(status);
        };
    }
    private String trackingDeliveryLabel(String status,String staffName) {
        return switch(status) {
            case "ACCEPTED" -> staffName==null || staffName.isBlank() ? "Nhân viên đã nhận nhiệm vụ" : "Nhân viên "+staffName.strip()+" đã nhận nhiệm vụ";
            case "PICKED_UP" -> "Nhân viên giao hàng đã lấy hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "FAILED" -> "Giao hàng thất bại";
            case "REDELIVERY_SCHEDULED" -> "Đã hẹn giao lại";
            case "CANCELLED" -> "Giao hàng đã hủy";
            default -> null;
        };
    }
    private Order owned(Long customerId, Long orderId) {
        return orders.findByOrderIdAndCustomerUserId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
    }

    private User lockCustomer(Long customerId) {
        User user = users.findByUserIdForUpdate(customerId)
                .orElseThrow(() -> new CustomerOrderException("Tài khoản không hợp lệ."));
        em.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new CustomerOrderException("Tài khoản không còn được phép thao tác đơn hàng.");
        }
        return user;
    }
}
