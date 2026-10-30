package VietFreshHub.order.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.*;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.delivery.service.DeliveryStatusHistoryWriter;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderCancelledEvent;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.service.PaymentService;
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
    private final PaymentService paymentService;
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
        String statusText=filter.getStatus()==null ? "" : filter.getStatus().trim();
        if(!statusText.isEmpty() && !OrderLabels.customerStatusOptions().containsKey(statusText))
            throw new CustomerOrderException("Trạng thái lọc không hợp lệ.");
        filter.setKeyword(filter.getKeyword().trim()); filter.setStatus(statusText);
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime from=filter.getFromDate()==null ? null : LocalDateTime.ofInstant(filter.getFromDate().atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        if(from!=null && from.getYear()<1) from=LocalDateTime.of(1,1,1,0,0);
        LocalDateTime until=filter.getToDate()==null ? null : LocalDateTime.ofInstant(filter.getToDate().plusDays(1).atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        Long referenceId=VietFreshHub.common.BusinessCodes.parse("VFH",filter.getKeyword());
        var page=shopOrders.findCustomerHistory(customerId,statusText,filter.getKeyword(),referenceId,from,until,PageRequest.of(filter.getPage()-1,10));
        if(page.getTotalElements()==0) return Page.empty(PageRequest.of(0,10));
        if(page.getNumber()>=page.getTotalPages())
            page=shopOrders.findCustomerHistory(customerId,statusText,filter.getKeyword(),referenceId,from,until,PageRequest.of(page.getTotalPages()-1,10));
        return page;
    }

    @Override @Transactional(readOnly=true)
    public OrderActionsResponse actions(Long customerId,Long orderId) {
        Order order=owned(customerId,orderId); var children=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId); var delivery=deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        String hint=cancellationHint(order,children,pay,delivery);
        return new OrderActionsResponse(hint==null,hint,pay.isEmpty() ? "Chưa có phương thức thanh toán" : pay.getFirst().getPaymentMethod().getName());
    }
    @Override @Transactional(readOnly=true)
    public OrderActionsResponse shopActions(Long customerId,Long shopOrderId) {
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(shop.getOrder().getOrderId());
        var delivery=deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(shopOrderId));
        String hint=cancellationHint(shop.getOrder(),List.of(shop),pay,delivery,true);
        return new OrderActionsResponse(hint==null,hint,pay.isEmpty() ? "Chưa có phương thức thanh toán" : pay.getFirst().getPaymentMethod().getName());
    }

    @Override @Transactional
    public void cancelShop(Long customerId,Long shopOrderId,String reason) {
        if(reason!=null && reason.length()>500) throw new CustomerOrderException("Lý do hủy tối đa 500 ký tự.");
        User actor=lockCustomer(customerId);
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        Order order=shop.getOrder();
        em.refresh(order,LockModeType.PESSIMISTIC_WRITE);
        em.refresh(shop,LockModeType.PESSIMISTIC_WRITE);
        if(!customerId.equals(order.getCustomer().getUserId()) || !order.getOrderId().equals(shop.getOrder().getOrderId()))
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
        if("CANCELLED".equals(shop.getStatus())) return;
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(order.getOrderId());
        pay.forEach(payment -> em.refresh(payment,LockModeType.PESSIMISTIC_WRITE));
        var delivery=deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(shopOrderId));
        delivery.forEach(row -> em.refresh(row,LockModeType.PESSIMISTIC_READ));
        String hint=cancellationHint(order,List.of(shop),pay,delivery,true);
        if(hint!=null) throw new CustomerOrderException(hint);
        var lines=shop.getItems().stream().map(item -> new OrderInventoryReleaseService.Line(
                item.getOrderItemId(),item.getVariant().getVariantId(),item.getQuantity())).toList();
        inventory.release(lines,customerId);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        shop.setStatus("CANCELLED");
        shop.setCancelledAt(now);
        OrderStatusHistory history=new OrderStatusHistory();
        history.setShopOrder(shop); history.setStatus("CANCELLED"); history.setChangedBy(actor);
        history.setNote("Khách hàng hủy đơn."+(reason==null || reason.isBlank() ? "" : " Lý do: "+reason.trim()));
        em.persist(history);
        var remaining=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(order.getOrderId()).stream()
                .filter(child -> !"CANCELLED".equals(child.getStatus())).toList();
        if(remaining.isEmpty()) {
            order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
            pay.stream().filter(payment -> payment.getStatus()!=Payment.Status.PAID)
                    .forEach(payment -> payment.setStatus(Payment.Status.CANCELLED));
        }
        var codAmount=remaining.stream().map(ShopOrder::getTotalAmount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        pay.stream().filter(payment -> "COD".equals(payment.getPaymentMethod().getMethodCode())
                && payment.getStatus()==Payment.Status.PENDING).forEach(payment -> payment.setAmount(codAmount));
        em.flush();
        if(remaining.isEmpty()) events.publishEvent(new OrderCancelledEvent(order.getOrderId(),customerId));
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
        pay.stream().filter(p -> p.getStatus()!=Payment.Status.PAID)
                .forEach(p -> p.setStatus(Payment.Status.CANCELLED));
        order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
        em.flush(); events.publishEvent(new OrderCancelledEvent(orderId,customerId));
    }
    private String cancellationHint(Order order,List<ShopOrder> children,List<Payment> pay,List<OrderDeliveryView> delivery) {
        return cancellationHint(order,children,pay,delivery,false);
    }
    private String cancellationHint(Order order,List<ShopOrder> children,List<Payment> pay,List<OrderDeliveryView> delivery,boolean shopOnly) {
        if(order.getStatus()==Order.Status.CANCELLED) return "Đơn hàng đã được hủy.";
        if(children.isEmpty()) return "Đơn hàng chưa có thông tin cửa hàng, không thể hủy.";
        if(children.stream().anyMatch(c -> "CANCELLED".equals(c.getStatus())))
            return shopOnly ? "Đơn cửa hàng đã được hủy." : "Một đơn cửa hàng đã được hủy. Không thể hủy lại hoặc hủy một phần đơn này.";
        if(children.stream().anyMatch(c -> !"PENDING".equals(c.getStatus()) || c.getConfirmedAt()!=null))
            return children.size()==1 ? "Đơn hàng đã được cửa hàng xác nhận nên không thể hủy."
                    : "Một hoặc nhiều cửa hàng đã xác nhận đơn hàng. Bạn không thể hủy toàn bộ đơn này.";
        if(!shopOnly && order.getStatus()!=Order.Status.PENDING) return "Đơn đã bắt đầu xử lý, không còn đủ điều kiện hủy.";
        if(delivery.stream().anyMatch(d -> !Set.of("UNASSIGNED","ASSIGNED","ACCEPTED","CANCELLED").contains(d.getStatus())))
            return "Đơn đã được lấy hoặc đang giao, không thể hủy.";
        String paymentStatus=shopOnly ? children.getFirst().getPaymentStatus() : order.getPaymentStatus();
        boolean paidOnline="PAID".equals(paymentStatus) && order.getGrandTotal()!=null
                && paymentService.getPaidOnlineOrderAmount(order.getOrderId())
                .filter(amount -> amount.compareTo(order.getGrandTotal())==0).isPresent();
        if(!paidOnline && ("PAID".equals(paymentStatus) || "PARTIALLY_REFUNDED".equals(paymentStatus) || "REFUNDED".equals(paymentStatus)
                || pay.stream().anyMatch(p -> !"COD".equals(p.getPaymentMethod().getMethodCode()) || p.getPaidAt()!=null
                || !Set.of(Payment.Status.PENDING,Payment.Status.FAILED,Payment.Status.CANCELLED).contains(p.getStatus()))))
            return "Đơn có giao dịch cần đối soát/hoàn tiền. Vui lòng liên hệ hỗ trợ để hủy an toàn.";
        if(order.getDiscountTotal().signum()>0 || children.stream().anyMatch(c -> c.getDiscountTotal().signum()>0))
            return "Đơn có ưu đãi cần đối soát trước khi hủy. Vui lòng liên hệ hỗ trợ.";
        return null;
    }

    @Override @Transactional
    public ReorderResult reorder(Long customerId,Long orderId) {
        User buyer=lockCustomer(customerId); owned(customerId,orderId);
        var lines=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId).stream().flatMap(s -> s.getItems().stream()).toList();
        Map<Long,Long> quantities=new TreeMap<>(); Map<Long,OrderItem> original=new HashMap<>();
        lines.forEach(line -> { Long id=line.getVariant().getVariantId(); quantities.merge(id,line.getQuantity().longValue(),Long::sum); original.putIfAbsent(id,line); });
        Cart cart=carts.findByUserUserIdAndStatus(customerId,"ACTIVE").orElse(null);
        List<ReorderResult.SkippedItem> skipped=new ArrayList<>(); int added=0; long units=0;
        for(var entry:quantities.entrySet()) {
            var past=original.get(entry.getKey()); var variant=variants.findById(entry.getKey()).orElse(null);
            String rejection=null; CartItem existing=cart==null ? null : cartItems.findByCartCartIdAndVariantVariantId(cart.getCartId(),entry.getKey()).orElse(null);
            long desired=entry.getValue()+(existing==null ? 0L : existing.getQuantity());
            if(variant==null || !catalog.isPurchasable(variant)) rejection="Sản phẩm hoặc cửa hàng hiện không nhận đơn.";
            else if(entry.getValue()<1 || desired>Integer.MAX_VALUE || desired>stock.getAvailableStockByVariantId(entry.getKey())) rejection="Không đủ hàng cho số lượng mua lại và số lượng đang có trong giỏ.";
            if(rejection!=null) { skipped.add(new ReorderResult.SkippedItem(past.getProductNameSnapshot(),past.getVariantNameSnapshot(),rejection)); continue; }
            if(cart==null) { cart=new Cart(); cart.setUser(buyer); carts.save(cart); }
            if(existing==null) { existing=new CartItem(); existing.setCart(cart); existing.setVariant(variant); }
            existing.setQuantity((int)desired); existing.setUnitPriceSnapshot(variant.getPrice()); cartItems.save(existing);
            added++; units+=entry.getValue();
        }
        return new ReorderResult(added,units,List.copyOf(skipped));
    }

    @Override @Transactional(readOnly=true)
    public OrderTrackingResponse tracking(Long customerId,Long orderId) {
        Order order=owned(customerId,orderId);
        return tracking(customerId,order,shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId));
    }

    @Override @Transactional(readOnly=true)
    public OrderTrackingResponse shopTracking(Long customerId,Long shopOrderId) {
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var tracking=tracking(customerId,shop.getOrder(),List.of(shop));
        var state=tracking.shops().getFirst();
        return new OrderTrackingResponse(tracking.orderId(),shop.getShopOrderCode(),state.deliveryStatus(),state.deliveryStatusCode(),tracking.shops());
    }

    private OrderTrackingResponse tracking(Long customerId,Order order,List<ShopOrder> children) {
        Long orderId=order.getOrderId();
        var shopOrderIds=children.stream().map(ShopOrder::getShopOrderId).toList();
        var rows=shopOrderIds.isEmpty() ? List.<OrderDeliveryView>of() : deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(shopOrderIds);
        var history=rows.isEmpty() ? List.<OrderDeliveryHistoryView>of() : deliveryHistory.findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(rows.stream().map(OrderDeliveryView::getDeliveryId).toList());
        var statuses=shopOrderIds.isEmpty() ? List.<OrderStatusHistory>of() : orderHistory.findByShopOrderShopOrderIdInOrderByCreatedAtAscHistoryIdAsc(shopOrderIds);
        var currentStatuses=shopOrderIds.isEmpty() ? List.<CustomerShopOrderStatus>of() : shopOrders.findCustomerStatuses(customerId,shopOrderIds);
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
                    .forEach(h -> timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(h.getCreatedAt()),OrderLabels.customerStatus(h.getStatus()),h.getNote(),"ORDER")));
            if(delivery!=null) {
                for(var event:deliveryEvents) {
                    String staffName=event.getChangedBy()==null ? null : trackingStaffName(staff,delivery.getDeliveryId(),event.getChangedBy());
                    boolean goodsReturned="FAILED".equals(event.getStatus()) && event.getChangedBy()!=null
                            && event.getNote()!=null && event.getNote().startsWith(DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX);
                    String eventLabel=goodsReturned ? null : trackingDeliveryLabel(event.getStatus(),staffName);
                    if(eventLabel!=null) timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(event.getCreatedAt()),eventLabel,null,"DELIVERY"));
                }
            }
            timeline.sort(Comparator.comparing(OrderTrackingResponse.Event::occurredAt));
            addMissingConfirmation(child,shopHistory,timeline);
            var currentStatus=currentStatuses.stream().filter(s -> s.shopOrderId().equals(child.getShopOrderId())).findFirst().orElse(null);
            String deliveryStatusCode=currentStatus==null ? null : currentStatus.status();
            String label=OrderLabels.customerStatus(deliveryStatusCode);
            if(delivery!=null && "ACCEPTED".equals(deliveryStatusCode)) {
                String staffName=trackingStaffName(staff,delivery.getDeliveryId(),null);
                label=trackingDeliveryLabel(deliveryStatusCode,staffName);
            }
            return new OrderTrackingResponse.ShopTracking(child.getShopOrderId(),child.getShop().getShopName(),OrderLabels.customerStatus(child.getStatus()),
                    child.getStatus(),label,deliveryStatusCode,List.copyOf(timeline));
        }).toList();
        return new OrderTrackingResponse(orderId,order.getOrderCode(),OrderLabels.order(order.getStatus()),order.getStatus().name(),groups);
    }
    private void addMissingConfirmation(ShopOrder child,List<OrderStatusHistory> history,List<OrderTrackingResponse.Event> timeline) {
        if(history.stream().anyMatch(entry -> "CONFIRMED".equals(entry.getStatus()))) return;
        Set<String> confirmedStates=Set.of("CONFIRMED","PREPARING","READY_FOR_DELIVERY","OUT_FOR_DELIVERY","COMPLETED");
        boolean confirmed=child.getConfirmedAt()!=null || confirmedStates.contains(child.getStatus())
                || history.stream().anyMatch(entry -> confirmedStates.contains(entry.getStatus()));
        if(!confirmed) return;
        var confirmation=new OrderTrackingResponse.Event(OrderLabels.local(child.getConfirmedAt()),OrderLabels.customerStatus("CONFIRMED"),null,"ORDER");
        int position=0;
        for(int i=0;i<timeline.size();i++) {
            var entry=timeline.get(i);
            if("ORDER".equals(entry.kind()) && OrderLabels.customerStatus("PENDING").equals(entry.label())) position=i+1;
        }
        timeline.add(position,confirmation);
    }
    private String trackingStaffName(List<DeliveryTrackingStaff> staff,Long deliveryId,Long staffId) {
        return staff.stream().filter(s -> s.deliveryId().equals(deliveryId) && (staffId==null || s.staffId().equals(staffId)))
                .findFirst().map(DeliveryTrackingStaff::staffName).orElse(null);
    }
    private String trackingDeliveryLabel(String status,String staffName) {
        return switch(status) {
            case "ACCEPTED" -> staffName==null || staffName.isBlank() ? "Nhân viên đã nhận nhiệm vụ" : "Nhân viên "+staffName.strip()+" đã nhận nhiệm vụ";
            case "PICKED_UP" -> "Nhân viên giao hàng đã lấy hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "FAILED" -> "Giao hàng thất bại";
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
