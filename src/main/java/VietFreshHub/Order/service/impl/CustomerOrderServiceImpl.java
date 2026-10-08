package VietFreshHub.Order.service.impl;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.entity.Cart;
import VietFreshHub.Cart.entity.CartItem;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Order.dto.*;
import VietFreshHub.Order.entity.*;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.event.OrderCancelledEvent;
import VietFreshHub.Order.exception.CustomerOrderException;
import VietFreshHub.Order.port.OrderInventoryReleasePort;
import VietFreshHub.Order.repository.*;
import VietFreshHub.Order.service.CustomerOrderService;
import VietFreshHub.Order.util.OrderLabels;
import VietFreshHub.Payment.entity.Payment;
import VietFreshHub.Payment.repository.PaymentRepository;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.Product.service.CatalogService;
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
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductVariantRepository variants;
    private final CatalogService catalog;
    private final InventoryBatchRepository stock;
    private final OrderInventoryReleasePort inventory;
    private final EntityManager em;
    private final ApplicationEventPublisher events;

    @Override @Transactional(readOnly=true)
    public Page<OrderListRow> history(Long customerId,OrderFilterRequest filter) {
        if(filter.getPage()<1 || filter.getPage()>10000 || filter.getKeyword()==null || filter.getKeyword().length()>100 || !filter.isDateRangeValid())
            throw new CustomerOrderException("Bộ lọc đơn hàng không hợp lệ.");
        Order.Status status;
        try { status=filter.getStatus()==null || filter.getStatus().isBlank() ? null : Order.Status.valueOf(filter.getStatus()); }
        catch(IllegalArgumentException ex) { throw new CustomerOrderException("Trạng thái lọc không hợp lệ."); }
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime from=filter.getFromDate()==null ? null : LocalDateTime.ofInstant(filter.getFromDate().atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        LocalDateTime until=filter.getToDate()==null ? null : LocalDateTime.ofInstant(filter.getToDate().plusDays(1).atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        return orders.findHistory(customerId,status,filter.getKeyword().trim(),from,until,
                PageRequest.of(filter.getPage()-1,10,Sort.by(Sort.Order.desc("placedAt"),Sort.Order.desc("orderId"))));
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
                .map(item -> new OrderInventoryReleasePort.Line(item.getOrderItemId(),item.getVariant().getVariantId(),item.getQuantity())).toList();
        inventory.release(lines,customerId);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        String note="Khách hàng hủy đơn."+(reason==null || reason.isBlank() ? "" : " Lý do: "+reason.trim());
        for(var child:children) if(child.getStatus()!=ShopOrder.Status.CANCELLED) {
            child.setStatus(ShopOrder.Status.CANCELLED); child.setCancelledAt(now);
            OrderStatusHistory history=new OrderStatusHistory(); history.setShopOrder(child); history.setStatus("CANCELLED"); history.setChangedBy(actor); history.setNote(note); em.persist(history);
        }
        pay.forEach(p -> p.setStatus(Payment.Status.CANCELLED)); order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
        em.flush(); events.publishEvent(new OrderCancelledEvent(orderId,customerId));
    }
    private String cancellationHint(Order order,List<ShopOrder> children,List<Payment> pay,List<OrderDeliveryView> delivery) {
        if(order.getStatus()==Order.Status.CANCELLED) return "Đơn hàng đã được hủy.";
        if(order.getStatus()!=Order.Status.PENDING && order.getStatus()!=Order.Status.CONFIRMED) return "Đơn đã bắt đầu xử lý, không còn đủ điều kiện hủy.";
        if(children.isEmpty() || children.stream().anyMatch(c -> c.getStatus()!=ShopOrder.Status.PENDING && c.getStatus()!=ShopOrder.Status.CONFIRMED && c.getStatus()!=ShopOrder.Status.CANCELLED))
            return "Một cửa hàng đã bắt đầu chuẩn bị hàng. Không thể hủy toàn bộ đơn.";
        if(delivery.stream().anyMatch(d -> !Set.of("UNASSIGNED","ASSIGNED","ACCEPTED","CANCELLED").contains(d.getStatus())))
            return "Đơn đã được lấy hoặc đang giao, không thể hủy.";
        if(order.getPaymentStatus()==Order.PaymentState.PAID || order.getPaymentStatus()==Order.PaymentState.PARTIALLY_REFUNDED || order.getPaymentStatus()==Order.PaymentState.REFUNDED
                || pay.stream().anyMatch(p -> !"COD".equals(p.getPaymentMethod().getMethodCode()) || p.getPaidAt()!=null
                || !Set.of(Payment.Status.PENDING,Payment.Status.FAILED,Payment.Status.CANCELLED).contains(p.getStatus())))
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
        Order order=owned(customerId,orderId); var children=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        var rows=deliveries.findByShopOrderOrderOrderIdOrderByDeliveryIdAsc(orderId);
        var history=rows.isEmpty() ? List.<OrderDeliveryHistoryView>of() : deliveryHistory.findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(rows.stream().map(OrderDeliveryView::getDeliveryId).toList());
        var statuses=orderHistory.findByShopOrderOrderOrderIdOrderByCreatedAtAscHistoryIdAsc(orderId);
        var groups=children.stream().map(child -> {
            OrderDeliveryView delivery=rows.stream().filter(d -> d.getShopOrder().getShopOrderId().equals(child.getShopOrderId())).findFirst().orElse(null);
            List<OrderTrackingResponse.Event> timeline=new ArrayList<>();
            statuses.stream().filter(h -> h.getShopOrder().getShopOrderId().equals(child.getShopOrderId()))
                    .forEach(h -> timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(h.getCreatedAt()),OrderLabels.shop(h.getStatus()),h.getNote(),"ORDER")));
            if(delivery!=null) history.stream().filter(h -> h.getDelivery().getDeliveryId().equals(delivery.getDeliveryId()))
                    .forEach(h -> timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(h.getCreatedAt()),OrderLabels.delivery(h.getStatus()),h.getNote(),"DELIVERY")));
            timeline.sort(Comparator.comparing(OrderTrackingResponse.Event::occurredAt));
            String label=child.getStatus()==ShopOrder.Status.CANCELLED ? "Đơn hàng đã hủy" : delivery==null ? "Chưa chuyển sang giao hàng" : OrderLabels.delivery(delivery.getStatus());
            return new OrderTrackingResponse.ShopTracking(child.getShopOrderId(),child.getShop().getShopName(),OrderLabels.shop(child.getStatus().name()),label,
                    delivery==null ? null : delivery.getFailureReason(),List.copyOf(timeline));
        }).toList();
        return new OrderTrackingResponse(orderId,order.getOrderCode(),OrderLabels.order(order.getStatus()),groups);
    }
    private Order owned(Long customerId,Long orderId) { return orders.findByOrderIdAndCustomerUserId(orderId,customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.")); }
    private User lockCustomer(Long customerId) {
        User user=users.findForCartUpdate(customerId).orElseThrow(() -> new CustomerOrderException("Tài khoản không hợp lệ."));
        em.refresh(user,LockModeType.PESSIMISTIC_WRITE); if(!"ACTIVE".equals(user.getStatus())) throw new CustomerOrderException("Tài khoản không còn được phép thao tác đơn hàng."); return user;
    }
}
