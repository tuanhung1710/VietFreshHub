package VietFreshHub.order.service.impl;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.order.service.OrderCancelledEvent;
import VietFreshHub.order.service.OrderLabels;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.*;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.repository.*;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
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

    @Override @Transactional(readOnly=true)
    public Page<OrderListRow> history(Long customerId,CustomerOrderFilterRequest filter) {
        if(filter==null || filter.getPage()<1 || filter.getPage()>10000 || filter.getKeyword()==null
                || filter.getKeyword().length()>100 || !filter.isDateRangeValid() || !filter.isDatabaseDateRangeValid())
            throw new CustomerOrderException("Bộ lọc đơn hàng không hợp lệ.");
        Order.Status status;
        String statusText=filter.getStatus()==null ? "" : filter.getStatus().trim();
        boolean delivering = "OUT_FOR_DELIVERY".equals(statusText);
        if ("PROCESSING".equals(statusText) || "PARTIALLY_COMPLETED".equals(statusText)) {
            throw new CustomerOrderException("Trạng thái lọc không hợp lệ.");
        }
        try { status=statusText.isEmpty() || delivering ? null : Order.Status.valueOf(statusText); }
        catch(IllegalArgumentException ex) { throw new CustomerOrderException("Trạng thái lọc không hợp lệ."); }
        filter.setKeyword(filter.getKeyword().trim()); filter.setStatus(statusText);
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime from=filter.getFromDate()==null ? null : LocalDateTime.ofInstant(filter.getFromDate().atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        // DATETIME2 cannot bind year zero, which can occur at VN midnight on 0001-01-01.
        if(from!=null && from.getYear()<1) from=LocalDateTime.of(1,1,1,0,0);
        // SQL Server cannot bind year 10000; its maximum date already includes every stored row.
        LocalDateTime until=filter.getToDate()==null || filter.getToDate().equals(LocalDate.of(9999,12,31))
                ? null : LocalDateTime.ofInstant(filter.getToDate().plusDays(1).atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        var sort=Sort.by(Sort.Order.desc("placedAt"),Sort.Order.desc("orderId"));
        var page=orders.findHistory(customerId,status,filter.getKeyword(),from,until,delivering,PageRequest.of(filter.getPage()-1,10,sort));
        if(page.getTotalElements()==0) return Page.empty(PageRequest.of(0,10,sort));
        if(page.getNumber()>=page.getTotalPages())
            return orders.findHistory(customerId,status,filter.getKeyword(),from,until,delivering,PageRequest.of(page.getTotalPages()-1,10,sort));
        return page;
    }

    @Override @Transactional(readOnly=true)
    public OrderActionsResponse actions(Long customerId,Long orderId) {
        Order order=owned(customerId,orderId); var children=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId); var delivery=deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        String hint=cancellationHint(order,children,pay,delivery);
        return new OrderActionsResponse(hint==null,hint,pay.isEmpty() ? "Chưa có phương thức thanh toán" : pay.getFirst().getPaymentMethod().getName());
    }
    @Override @Transactional
    public void cancel(Long customerId,Long orderId,String reason) {
        if(reason!=null && reason.length()>500) throw new CustomerOrderException("Lý do hủy tối đa 500 ký tự.");
        User actor=lockCustomer(customerId); Order order=owned(customerId,orderId); em.refresh(order,LockModeType.PESSIMISTIC_WRITE);
        if(order.getStatus()==Order.Status.CANCELLED) return;
        var children=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        children.forEach(child -> em.refresh(child,LockModeType.PESSIMISTIC_WRITE));
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId); pay.forEach(p -> em.refresh(p,LockModeType.PESSIMISTIC_WRITE));
        var delivery=deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId); delivery.forEach(d -> em.refresh(d,LockModeType.PESSIMISTIC_READ));
        String hint=cancellationHint(order,children,pay,delivery); if(hint!=null) throw new CustomerOrderException(hint);
        var lines=children.stream().flatMap(child -> child.getItems().stream())
                .map(item -> new OrderInventoryReleaseService.Line(item.getOrderItemId(),item.getVariant().getVariantId(),item.getQuantity())).toList();
        inventory.release(lines,customerId);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        String note="Khách hàng hủy đơn."+(reason==null || reason.isBlank() ? "" : " Lý do: "+reason.trim());
        for(var child:children) if(!"CANCELLED".equals(child.getStatus())) {
            child.setStatus("CANCELLED"); child.setCancelledAt(now);
            OrderStatusHistory history=new OrderStatusHistory(); history.setShopOrder(child); history.setStatus("CANCELLED"); history.setChangedBy(actor); history.setNote(note); em.persist(history);
        }
        pay.forEach(p -> p.setStatus(Payment.Status.CANCELLED)); order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
        em.flush(); events.publishEvent(new OrderCancelledEvent(orderId,customerId));
    }
    private String cancellationHint(Order order,List<ShopOrder> children,List<Payment> pay,List<OrderDeliveryView> delivery) {
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
        if (order.getStatus() != Order.Status.PENDING && order.getStatus() != Order.Status.CONFIRMED) {
            return "Đơn đã bắt đầu xử lý, không còn đủ điều kiện hủy.";
        }
        if(delivery.stream().anyMatch(d -> !Set.of("UNASSIGNED","ASSIGNED","ACCEPTED","CANCELLED").contains(d.getStatus())))
            return "Đơn đã được lấy hoặc đang giao, không thể hủy.";
        if("PAID".equals(order.getPaymentStatus()) || "PARTIALLY_REFUNDED".equals(order.getPaymentStatus()) || "REFUNDED".equals(order.getPaymentStatus())
                || pay.stream().anyMatch(p -> !"COD".equals(p.getPaymentMethod().getMethodCode()) || p.getPaidAt()!=null
                || !Set.of(Payment.Status.PENDING,Payment.Status.FAILED,Payment.Status.CANCELLED).contains(p.getStatus())))
            return "Đơn có giao dịch cần đối soát/hoàn tiền. Vui lòng liên hệ hỗ trợ để hủy an toàn.";
        if(order.getDiscountTotal().signum()>0 || children.stream().anyMatch(c -> c.getDiscountTotal().signum()>0))
            return "Đơn có ưu đãi cần đối soát trước khi hủy. Vui lòng liên hệ hỗ trợ.";
        return null;
    }

    @Override @Transactional
    public ReorderResult reorder(Long customerId,Long orderId) {
        User buyer = lockCustomer(customerId);
        owned(customerId, orderId);
        var children = shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
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
        for (var entry : quantities.entrySet()) {
            Long variantId = entry.getKey();
            long requestedQuantity = entry.getValue();
            OrderItem original = originalItems.get(variantId);
            var variant = variants.findById(variantId).orElse(null);
            CartItem existing = cart == null ? null : cartItems
                    .findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId).orElse(null);
            long desiredQuantity = requestedQuantity + (existing == null ? 0L : existing.getQuantity());

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
        var confirmation=new OrderTrackingResponse.Event(OrderLabels.local(child.getConfirmedAt()),trackingShopLabel("CONFIRMED"),null,"ORDER");
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
    private Order owned(Long customerId,Long orderId) { return orders.findByOrderIdAndCustomerUserId(orderId,customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.")); }
    private User lockCustomer(Long customerId) {
        User user=users.findByUserIdForUpdate(customerId).orElseThrow(() -> new CustomerOrderException("Tài khoản không hợp lệ."));
        em.refresh(user,LockModeType.PESSIMISTIC_WRITE); if(!"ACTIVE".equals(user.getStatus())) throw new CustomerOrderException("Tài khoản không còn được phép thao tác đơn hàng."); return user;
    }
}
